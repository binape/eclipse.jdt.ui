/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.core.refactoring.methods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.AbstractSyntaxTreeVisitorAdapter;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.lookup.*;
import org.eclipse.jdt.internal.compiler.util.CharOperation;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.refactoring.AbstractRefactoringASTAnalyzer;
import org.eclipse.jdt.internal.core.refactoring.DebugUtils;

/*
 * not API
 */
class ParameterOffsetFinder extends AbstractRefactoringASTAnalyzer{
	
	private IMethod fMethod;
	private int fParameterIndex;
	private char[] fParameterName; 
	private ISourceRange fMethodSourceRange;

	private List fOffsetsFound;
	private int fMethodSourceRangeEnd;
	private List fParamBindings;
	private boolean fIncludeReferences;
	
	ParameterOffsetFinder(IMethod method, int parameterIndex) throws JavaModelException{ 
		fMethod= method;
		fParameterIndex= parameterIndex;
		fParameterName= method.getParameterNames()[fParameterIndex].toCharArray();
		fMethodSourceRange= method.getSourceRange();
		fMethodSourceRangeEnd= fMethodSourceRange.getOffset() + fMethodSourceRange.getLength();
	}
	/**
	 * @param includeReferences if it is <code>true</code>, then not only the parameter declaration but also references will be included
	 * @return indices of offsets of the references to the parameter specified in constructor
	 */
	int[] findOffsets(boolean includeReferences) throws JavaModelException{
		fIncludeReferences= includeReferences;
		fOffsetsFound= new ArrayList();
		((CompilationUnit)fMethod.getCompilationUnit()).accept(this);
		return convertFromIntegerList(fOffsetsFound);
	}
	
	private static int[] convertFromIntegerList(List list){
		int[] result= new int[list.size()];
		Integer[] integerResult= (Integer[])list.toArray(new Integer[list.size()]);
		for (int i= 0; i < integerResult.length; i++){
			result[i]= integerResult[i].intValue();
		}
		return result;
	}
	
	private void addNodeOffset(AstNode node){
		fOffsetsFound.add(new Integer(node.sourceStart));
	}
	
	private boolean withinMethod(AstNode node){
		return (node.sourceStart >= fMethodSourceRange.getOffset()) 
			&& (node.sourceStart <= fMethodSourceRangeEnd);
	}
		
	private boolean isParameterMatch(SingleNameReference singleNameReference, BlockScope blockScope){
		if (! withinMethod(singleNameReference))
			return false;
		if (! CharOperation.equals(fParameterName, singleNameReference.token))
			return false;
		if (! fParamBindings.contains(singleNameReference.binding))
			return false;
		return true;	
	}
		
	//-------  visit methods  ---------
	
	public boolean visit(SingleNameReference singleNameReference, BlockScope blockScope){
		if (! fIncludeReferences)
			return true;
		
		if  (isParameterMatch(singleNameReference, blockScope))
			addNodeOffset(singleNameReference);
		return true;
	}
	
	public boolean visit(LocalDeclaration localDeclaration, BlockScope scope) {
		if (! fIncludeReferences)
			return true;
		
		if (withinMethod(localDeclaration) 
			&&	fParamBindings.contains(localDeclaration.binding))
				fOffsetsFound.add(new Integer(localDeclaration.declarationSourceEnd - localDeclaration.name.length));
		return true;
	}
	
	public boolean visit(MethodDeclaration methodDeclaration, ClassScope scope) {
		if (methodDeclaration.declarationSourceStart == fMethodSourceRange.getOffset())
			fParamBindings= RenameParameterASTAnalyzer.getArgumentBindings(methodDeclaration);
		return true;
	}
	
	public boolean visit(ConstructorDeclaration constructorDeclaration, ClassScope scope) {
		if (constructorDeclaration.declarationSourceStart == fMethodSourceRange.getOffset())
			fParamBindings= RenameParameterASTAnalyzer.getArgumentBindings(constructorDeclaration);
		return true;
	}
		
	public boolean visit(Argument argument, BlockScope scope) {
		if (withinMethod(argument) 
		 	&& fParamBindings.contains(argument.binding)
		 	&& CharOperation.equals(argument.name, fParameterName))
				fOffsetsFound.add(new Integer(argument.declarationSourceEnd - argument.name.length + 1));		
		return true;
	}
		
	public boolean visit(QualifiedNameReference qualifiedNameReference,	BlockScope scope) {
		if (! fIncludeReferences)
			return true;
		
		if (withinMethod(qualifiedNameReference) 
			&& CharOperation.equals(qualifiedNameReference.tokens[0], fParameterName)){
				fOffsetsFound.add(new Integer(qualifiedNameReference.sourceStart));
		}	
		return true;
	}
}