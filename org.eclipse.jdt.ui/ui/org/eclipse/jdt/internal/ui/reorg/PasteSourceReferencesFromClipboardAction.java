package org.eclipse.jdt.internal.ui.reorg;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.dnd.Clipboard;

import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.codemanipulation.MemberEdit;
import org.eclipse.jdt.internal.corext.refactoring.Assert;
import org.eclipse.jdt.internal.corext.refactoring.reorg.SourceReferenceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.WorkingCopyUtil;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBufferEditor;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.preferences.CodeFormatterPreferencePage;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringAction;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

class PasteSourceReferencesFromClipboardAction extends RefactoringAction {

	public PasteSourceReferencesFromClipboardAction(ISelectionProvider provider) {
		super("&Paste", provider);
	}

	/*
	 * @see RefactoringAction#canOperateOn(IStructuredSelection)
	 */
	public boolean canOperateOn(IStructuredSelection selection) {
		try{
			if (! isAnythingInInterestingClipboard())
				return false;
				
			if (selection.size() != 1)
				return false;				
			
			Object selected= selection.getFirstElement();
			if (selected instanceof IClassFile)
				return false;
				 
			if (! (selected instanceof ISourceReference))
				return false;
	
			if (! (selected instanceof IJavaElement))
				return false;
			
			if (((IJavaElement)selected).isReadOnly())
				return false;
			
			if (! ((IJavaElement)selected).exists())
				return false;
				
			IFile file= SourceReferenceUtil.getFile((ISourceReference)selected);
			if (file.isReadOnly())
				return false;	
			
			if (! file.isAccessible())
				return false;	
				
			if (selected instanceof IMember && ((IMember)selected).isBinary())
				return false;

			ISourceReference workingCopyEl= getWorkingCopyElement((ISourceReference)selected);
			if (workingCopyEl == null || ! ((IJavaElement)workingCopyEl).exists())
				return false;
			
			return canPaste((ISourceReference)selected, getClipboardContents());
		} catch (JavaModelException e){
			JavaPlugin.log(e);
			return false;
		}		
	}
	
	private static Clipboard getClipboard(){
		return new Clipboard(JavaPlugin.getActiveWorkbenchShell().getDisplay());
	}
	
	private static boolean isAnythingInInterestingClipboard(){
		TypedSource[] elems= (TypedSource[])getClipboard().getContents(TypedSourceTransfer.getInstance());
		if (elems == null)
			return false;
		for (int i= 0; i < elems.length; i++) {
			if (! isInterestingSourceReference(elems[i]))
				return false;
		}
		return true;
	}
	
	private static boolean isInterestingSourceReference(TypedSource je){
		if (je.getType() == IJavaElement.CLASS_FILE)
			return false;
		if (je.getType() == IJavaElement.COMPILATION_UNIT)
			return false;	
		return true;		
	}
	
	private static TypedSource[] getClipboardContents(){
		Assert.isTrue(isAnythingInInterestingClipboard());
		return (TypedSource[])getClipboard().getContents(TypedSourceTransfer.getInstance());
	}
	
	private static boolean canPaste(ISourceReference ref, TypedSource[] elements){
		return (canPasteIn(ref, elements) || canPasteAfter(ref, elements));
	}
	
	private ISourceReference getSelectedElement(){
		return (ISourceReference)getStructuredSelection().getFirstElement();
	}
	
	/*
	 * @see Action#run
	 */
	public void run() {
		if (! canOperateOn(getStructuredSelection()))
			return;
		
		new BusyIndicator().showWhile(JavaPlugin.getActiveWorkbenchShell().getDisplay(), new Runnable() {
			public void run() {
				try {
					perform(getSelectedElement());
				} catch (CoreException e) {
					ExceptionHandler.handle(e, "Paste", "Unexpected exception. See log for details.");
				}
			}
		});
	}
	
	static void perform(ISourceReference selected) throws CoreException{
		ISourceReference selectedWorkingCopyElement= getWorkingCopyElement(selected);
		if (selectedWorkingCopyElement == null || ! ((IJavaElement)selectedWorkingCopyElement).exists())
			return;
		
		if (canPasteIn(selectedWorkingCopyElement, getClipboardContents())){
			if (selectedWorkingCopyElement instanceof ICompilationUnit) //special case
				pasteInCompilationUnit((ICompilationUnit)selectedWorkingCopyElement);
			else
				paste(MemberEdit.ADD_AT_BEGINNING, selectedWorkingCopyElement);	
		}	else if (canPasteAfter(selectedWorkingCopyElement, getClipboardContents()))
			paste(MemberEdit.INSERT_AFTER, selectedWorkingCopyElement);
		else	
			Assert.isTrue(false);//should be checked already (on activation)	
	}

	private static ISourceReference getWorkingCopyElement(ISourceReference selected) throws JavaModelException {
		ICompilationUnit cu= SourceReferenceUtil.getCompilationUnit(selected);
		ICompilationUnit workingCopy= WorkingCopyUtil.getWorkingCopyIfExists(cu);
		return (ISourceReference)JavaModelUtil.findInCompilationUnit(workingCopy, (IJavaElement)selected);
	}
		
	private static void paste(int style, ISourceReference selected) throws CoreException{
		TextBuffer tb= TextBuffer.acquire(SourceReferenceUtil.getFile(selected));
		try{
			TextBufferEditor tbe= new TextBufferEditor(tb);
			TypedSource[] elems= getClipboardContents();
			
			IJavaElement element= (IJavaElement)selected;
			int tabWidth= CodeFormatterPreferencePage.getTabSize();
			for (int i= 0; i < elems.length; i++) {
				String[] source= new String[]{elems[i].getSource()};
				tbe.add(new MemberEdit(element, style, source, tabWidth));
			}
			if (! tbe.canPerformEdits())
				return; ///XXX
			tbe.performEdits(new NullProgressMonitor());	
			TextBuffer.commitChanges(tb, false, new NullProgressMonitor());
		} finally{	
			if (tb != null)
				TextBuffer.release(tb);
		}
	}
	
	private static void pasteInCompilationUnit(ICompilationUnit unit) throws CoreException{
		TextBuffer tb= TextBuffer.acquire(SourceReferenceUtil.getFile(unit));
		try{
			TextBufferEditor tbe= new TextBufferEditor(tb);
			TypedSource[] elems= getClipboardContents();
			
			for (int i= 0; i < elems.length; i++) {
				tbe.add(new PasteInCompilationUnitEdit(elems[i].getSource(), elems[i].getType(), unit));
			}
			if (! tbe.canPerformEdits())
				return; ///XXX
			tbe.performEdits(new NullProgressMonitor());	
			TextBuffer.commitChanges(tb, false, new NullProgressMonitor());
		} finally{	
			if (tb != null)
				TextBuffer.release(tb);		
		}	
	}
	
	private static boolean canPasteAfter(ISourceReference ref, TypedSource[] elements){
		if (ref instanceof ICompilationUnit)
			return false;
		if (ref instanceof IImportContainer)
			return canPasteAtTopLevel(elements);
		if (ref instanceof IImportDeclaration)
			return canPasteAtTopLevel(elements);
		if (ref instanceof IPackageDeclaration)
			return canPasteAtTopLevel(elements);
		
		//order important
		if (ref instanceof IType)
			return canPasteAfterType(elements);
				
		if (ref instanceof IMember)
			return  canPasteAfterMember(elements);
		return false;
	}
	
	private static boolean canPasteAfterType(TypedSource[] elems){
		return areAllValuesOfType(elems, IJavaElement.TYPE);
	}

	private static boolean canPasteAtTopLevel(TypedSource[] elements){
		for (int i= 0; i < elements.length; i++) {
			if (! canPasteAfterImportContainerOrDeclaration(elements[i].getType()))
				return false;
		}
		return true;
	}
	
	private static int getElementType(ISourceReference ref){
		return ((IJavaElement)ref).getElementType();
	}
	
	private static boolean canPasteAfterImportContainerOrDeclaration(int type){
		if (type == IJavaElement.IMPORT_CONTAINER)
			return true;
		if (type == IJavaElement.IMPORT_DECLARATION)	
			return true;
		if (type == IJavaElement.TYPE)		
			return true;	
		return false;
	}

	private static boolean canPasteAfterMember(TypedSource[] elems){
		return areAllMembers(elems);
	}
	
	private static boolean canPasteIn(ISourceReference ref, TypedSource[] elements){
		if (ref instanceof IImportContainer)
			return canPasteInImportContainer(elements);	
		if (ref instanceof IType)
			return canPasteInType(elements);
		if (ref instanceof ICompilationUnit)
			return canPasteInCompilationUnit(elements);
		
		return false;	
	}
	
	private static boolean canPasteInImportContainer(TypedSource[] elements){
		return areAllValuesOfType(elements, IJavaElement.IMPORT_DECLARATION);
	}
	
	private static boolean canPasteInType(TypedSource[] elements){
		return areAllMembers(elements);
	}
		
	private static boolean canPasteInCompilationUnit(TypedSource[] elements){
		for (int i= 0; i < elements.length; i++) {
			if (! canPasteInCompilationUnit(elements[i].getType()))
				return false;
		}
		return true;
	}
		
	private static boolean canPasteInCompilationUnit(int type){
		if (type == IJavaElement.IMPORT_CONTAINER)
			return true;
		if (type == IJavaElement.IMPORT_DECLARATION)	
			return true; //XXX maybe only when there is no ImportContainer?
		if (type == IJavaElement.PACKAGE_DECLARATION)		
			return true; //XXX even if there's one already?
		if (type == IJavaElement.TYPE)		
			return true;
		return false;	
	}	
	
	//--- helpers
	
	private static boolean areAllValuesOfType(TypedSource[] elements, int type){
		for (int i= 0; i < elements.length; i++) {
			if (elements[i].getType() != type)
				return false;
		}
		return true;
	}
	
	private static boolean areAllMembers(TypedSource[] elements){
		for (int i= 0; i < elements.length; i++) {
			if (! isMember(elements[i].getType()))
				return false;
		}
		return true;
	}

	private static boolean isMember(int type){
		if (type == IJavaElement.FIELD)
			return true;
		if (type == IJavaElement.INITIALIZER)
			return true;
		if (type == IJavaElement.METHOD)
			return true;
		if (type == IJavaElement.TYPE)
			return true;
		return false;		
	}
};