/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.nls;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.nls.KeyValuePair;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSSubstitution;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusDialog;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

class ExternalizeWizardPage extends UserInputWizardPage {

	private static final String[] PROPERTIES;
	private static final String[] fgTitles;
	private static final int STATE_PROP= 0;
	private static final int VAL_PROP= 1;
	private static final int KEY_PROP= 2;
	private static final int SIZE= 3; //column counter
	private static final int ROW_COUNT= 5;

	public static final String PAGE_NAME= "NLSWizardPage1"; //$NON-NLS-1$
	static {
		PROPERTIES= new String[SIZE];
		PROPERTIES[STATE_PROP]= "task"; //$NON-NLS-1$
		PROPERTIES[KEY_PROP]= "key"; //$NON-NLS-1$
		PROPERTIES[VAL_PROP]= "value"; //$NON-NLS-1$

		fgTitles= new String[SIZE];
		fgTitles[STATE_PROP]= ""; //$NON-NLS-1$
		fgTitles[KEY_PROP]= NLSUIMessages.getString("ExternalizeWizardPage.key"); //$NON-NLS-1$
		fgTitles[VAL_PROP]= NLSUIMessages.getString("ExternalizeWizardPage.value"); //$NON-NLS-1$
	}

	private class CellModifier implements ICellModifier {

		/**
		 * @see ICellModifier#canModify(Object, String)
		 */
		public boolean canModify(Object element, String property) {
			if (property == null)
				return false;

			if (!(element instanceof NLSSubstitution))
				return false;
			
			NLSSubstitution subst= (NLSSubstitution) element;
			if (PROPERTIES[KEY_PROP].equals(property) && subst.getState() != NLSSubstitution.EXTERNALIZED) {
				return false;
			}
			
			return true;
		}

		/**
		 * @see ICellModifier#getValue(Object, String)
		 */
		public Object getValue(Object element, String property) {
			if (element instanceof NLSSubstitution) {
				NLSSubstitution substitution= (NLSSubstitution) element;
				String res= null;
				if (PROPERTIES[KEY_PROP].equals(property)) {
					res= substitution.getKeyWithoutPrefix();
				} else if (PROPERTIES[VAL_PROP].equals(property)) {
					res= substitution.getValue();
				} else if (PROPERTIES[STATE_PROP].equals(property)) {
					return new Integer(substitution.getState());
				}
				if (res != null) {
					return res;
				}
				return ""; //$NON-NLS-1$
			}
			return ""; //$NON-NLS-1$
		}

		/**
		 * @see ICellModifier#modify(Object, String, Object)
		 */
		public void modify(Object element, String property, Object value) {
			if (element instanceof TableItem) {
				Object data= ((TableItem) element).getData();
				if (data instanceof NLSSubstitution) {
					NLSSubstitution substitution= (NLSSubstitution) data;
					if (PROPERTIES[KEY_PROP].equals(property)) {
						substitution.setKey((String) value);
						validateKeys();
					}
					if (PROPERTIES[VAL_PROP].equals(property)) {
						substitution.setValue((String) value);
						validateKeys();
					}
					if (PROPERTIES[STATE_PROP].equals(property)) {
						substitution.setState(((Integer) value).intValue());
						if ((substitution.getState() == NLSSubstitution.EXTERNALIZED) && substitution.hasStateChanged()) {
							substitution.generateKey(fSubstitutions);
						}
					}
				}
				fTableViewer.refresh();
			}
		}
	}

	private class NLSSubstitutionLabelProvider extends LabelProvider implements ITableLabelProvider, IFontProvider {

		private Font fBold;

		public NLSSubstitutionLabelProvider() {
			FontRegistry fontRegistry= PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getFontRegistry();
			fBold= fontRegistry.getBold(JFaceResources.DIALOG_FONT);
		}

		public String getColumnText(Object element, int columnIndex) {
			String columnText= ""; //$NON-NLS-1$
			if (element instanceof NLSSubstitution) {
				NLSSubstitution substitution= (NLSSubstitution) element;
				if (columnIndex == KEY_PROP) {
					if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
						columnText= substitution.getKey();
					}
				} else
					if ((columnIndex == VAL_PROP) && (substitution.getValue() != null)) {
						columnText= substitution.getValue();
					}
			}
			return unwindEscapeChars(columnText);
		}

		private String unwindEscapeChars(String s) {
			if (s != null) {
				StringBuffer sb= new StringBuffer(s.length());
				int length= s.length();
				for (int i= 0; i < length; i++) {
					char c= s.charAt(i);
					sb.append(getUnwoundString(c));
				}
				return sb.toString();
			}
			return null;
		}

		private String getUnwoundString(char c) {
			switch (c) {
				case '\b' :
					return "\\b";//$NON-NLS-1$
				case '\t' :
					return "\\t";//$NON-NLS-1$
				case '\n' :
					return "\\n";//$NON-NLS-1$
				case '\f' :
					return "\\f";//$NON-NLS-1$	
				case '\r' :
					return "\\r";//$NON-NLS-1$
				case '\\' :
					return "\\\\";//$NON-NLS-1$
			}
			return String.valueOf(c);
		}

		public Image getColumnImage(Object element, int columnIndex) {
			if ((columnIndex == STATE_PROP) && (element instanceof NLSSubstitution)) {
				return getNLSImage((NLSSubstitution) element);
			}

			return null;
		}

		public Font getFont(Object element) {
			if (element instanceof NLSSubstitution) {
				NLSSubstitution substitution= (NLSSubstitution) element;
				if (substitution.hasPropertyFileChange() || substitution.hasSourceChange()) {
					return fBold;
				}
			}
			return null;
		}

		private Image getNLSImage(NLSSubstitution sub) {
			if ((sub.getValue() == null) && (sub.getKey() != null)) {
				JavaElementImageDescriptor imageDescriptor= new JavaElementImageDescriptor(getNLSImageDescriptor(sub.getState()), JavaElementImageDescriptor.ERROR, JavaElementImageProvider.SMALL_SIZE);
				return JavaPlugin.getImageDescriptorRegistry().get(imageDescriptor);
			} else
				if (sub.isConflicting(fSubstitutions)) {
					JavaElementImageDescriptor imageDescriptor= new JavaElementImageDescriptor(getNLSImageDescriptor(sub.getState()), JavaElementImageDescriptor.ERROR, JavaElementImageProvider.SMALL_SIZE);
					return JavaPlugin.getImageDescriptorRegistry().get(imageDescriptor);
				} else {
					return	getNLSImage(sub.getState());
				}
		}

		private Image getNLSImage(int task) {
			switch (task) {
				case NLSSubstitution.EXTERNALIZED :
					return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_NLS_TRANSLATE);
				case NLSSubstitution.IGNORED :
					return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_NLS_NEVER_TRANSLATE);
				case NLSSubstitution.INTERNALIZED :
					return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_NLS_SKIP);
				default :
					Assert.isTrue(false);
					return null;
			}
		}

		private ImageDescriptor getNLSImageDescriptor(int task) {
			switch (task) {
				case NLSSubstitution.EXTERNALIZED :
					return JavaPluginImages.DESC_OBJS_NLS_TRANSLATE;
				case NLSSubstitution.IGNORED :
					return JavaPluginImages.DESC_OBJS_NLS_NEVER_TRANSLATE;
				case NLSSubstitution.INTERNALIZED :
					return JavaPluginImages.DESC_OBJS_NLS_SKIP;
				default :
					Assert.isTrue(false);
					return null;
			}
		}
	}

	private class NLSInputDialog extends StatusDialog implements IDialogFieldListener {
		private StringDialogField fKeyField;
		private StringDialogField fValueField;
		private DialogField fMessageField;
		private NLSSubstitution fSubstitution;

		public NLSInputDialog(Shell parent, NLSSubstitution substitution) {
			super(parent);
			
			setTitle(NLSUIMessages.getString("ExternalizeWizardPage.NLSInputDialog.Title")); //$NON-NLS-1$

			fSubstitution= substitution;
			
			fMessageField= new DialogField();
			if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
				fMessageField.setLabelText(NLSUIMessages.getString("ExternalizeWizardPage.NLSInputDialog.ext.Label")); //$NON-NLS-1$
			} else {
				fMessageField.setLabelText(NLSUIMessages.getString("ExternalizeWizardPage.NLSInputDialog.Label")); //$NON-NLS-1$
			}

			fKeyField= new StringDialogField();
			fKeyField.setLabelText(NLSUIMessages.getString("ExternalizeWizardPage.NLSInputDialog.Enter_key")); //$NON-NLS-1$
			fKeyField.setDialogFieldListener(this);

			fValueField= new StringDialogField();
			fValueField.setLabelText(NLSUIMessages.getString("ExternalizeWizardPage.NLSInputDialog.Enter_value")); //$NON-NLS-1$
			fValueField.setDialogFieldListener(this);

			if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
				fKeyField.setText(substitution.getKeyWithoutPrefix());
			} else {
				fKeyField.setText(""); //$NON-NLS-1$
			}

			fValueField.setText(substitution.getValueNonEmpty());
		}

		public KeyValuePair getResult() {
			KeyValuePair res= new KeyValuePair(fKeyField.getText(), fValueField.getText());
			return res;
		}

		protected Control createDialogArea(Composite parent) {
			Composite composite= (Composite) super.createDialogArea(parent);

			Composite inner= new Composite(composite, SWT.NONE);
			GridLayout layout= new GridLayout();
			layout.marginHeight= 0;
			layout.marginWidth= 0;
			layout.numColumns= 2;
			inner.setLayout(layout);

			fMessageField.doFillIntoGrid(inner, 2);
			
			if (fSubstitution.getState() == NLSSubstitution.EXTERNALIZED) {
				fKeyField.doFillIntoGrid(inner, 2);
				LayoutUtil.setWidthHint(fKeyField.getTextControl(null), convertWidthInCharsToPixels(45));
			}
			
			fValueField.doFillIntoGrid(inner, 2);
			LayoutUtil.setWidthHint(fValueField.getTextControl(null), convertWidthInCharsToPixels(45));
			LayoutUtil.setHorizontalGrabbing(fValueField.getTextControl(null));

			fValueField.postSetFocusOnDialogField(parent.getDisplay());

			applyDialogFont(composite);
			return composite;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener#dialogFieldChanged(org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField)
		 */
		public void dialogFieldChanged(DialogField field) {
			IStatus keyStatus= validateKey(fKeyField.getText()); //$NON-NLS-1$
			//IStatus valueStatus= StatusInfo.OK_STATUS; // no validation yet

			//updateStatus(StatusUtil.getMoreSevere(valueStatus, keyStatus));
			updateStatus(keyStatus);
		}


		private IStatus validateKey(String val) {
			if (fSubstitution.getState() != NLSSubstitution.EXTERNALIZED) {
				return StatusInfo.OK_STATUS;
			}
			
			if (val.length() == 0) {
				return new StatusInfo(IStatus.ERROR, NLSUIMessages.getString("ExternalizeWizardPage.NLSInputDialog.Error_empty_key")); //$NON-NLS-1$
			}
			// validation so keys don't contain spaces
			for (int i= 0; i < val.length(); i++) {
				if (Character.isWhitespace(val.charAt(i))) {
					return new StatusInfo(IStatus.ERROR, NLSUIMessages.getString("ExternalizeWizardPage.NLSInputDialog.Error_invalid_key")); //$NON-NLS-1$
				}
			}
			return StatusInfo.OK_STATUS;
		}
	}

	private Text fPrefixField;
	private Table fTable;
	private TableViewer fTableViewer;
	private SourceViewer fSourceViewer;

	private final ICompilationUnit fCu;
	private NLSSubstitution[] fSubstitutions;
	private Button fExternalizeButton;
	private Button fIgnoreButton;
	private Button fInternalizeButton;
	private Button fRevertButton;
	private Button fEditButton;
	private NLSRefactoring fNLSRefactoring;
	private Button fRenameButton;
	private Text fAccessorClassField;
	private Text fPropertiesFileField;
	private Button fFilterCheckBox;

	public ExternalizeWizardPage(NLSRefactoring nlsRefactoring) {
		super(PAGE_NAME);
		fCu= nlsRefactoring.getCu();
		fSubstitutions= nlsRefactoring.getSubstitutions();
		fNLSRefactoring= nlsRefactoring;

		createDefaultExternalization(fSubstitutions, nlsRefactoring.getPrefix());
	}

	/*
	 * @see IDialogPage#createControl(Composite)
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);

		Composite supercomposite= new Composite(parent, SWT.NONE);
		supercomposite.setLayout(new GridLayout());

		createKeyPrefixField(supercomposite);

		SashForm composite= new SashForm(supercomposite, SWT.VERTICAL);

		GridData data= new GridData(GridData.FILL_BOTH);
		composite.setLayoutData(data);

		createTableViewer(composite);
		createSourceViewer(composite);
		
		createAccessorInfoComposite(supercomposite);

		composite.setWeights(new int[]{65, 45});

		validateKeys();

		// promote control
		setControl(supercomposite);
		Dialog.applyDialogFont(supercomposite);
		WorkbenchHelp.setHelp(supercomposite, IJavaHelpContextIds.EXTERNALIZE_WIZARD_KEYVALUE_PAGE);
	}

	/**
	 * @param supercomposite
	 */
	private void createAccessorInfoComposite(Composite supercomposite) {
		Composite accessorComposite= new Composite(supercomposite, SWT.NONE);
		accessorComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		GridLayout layout= new GridLayout(2, false);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		accessorComposite.setLayout(layout);
		
		Composite composite= new Composite(accessorComposite, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		layout= new GridLayout(2, true);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		composite.setLayout(layout);
		
		Label accessorClassLabel= new Label(composite, SWT.NONE);
		accessorClassLabel.setText(NLSUIMessages.getString("ExternalizeWizardPage.accessorclass.label")); //$NON-NLS-1$
		accessorClassLabel.setLayoutData(new GridData());
		
		Label propertiesFileLabel= new Label(composite, SWT.NONE);
		propertiesFileLabel.setText(NLSUIMessages.getString("ExternalizeWizardPage.propertiesfile.label")); //$NON-NLS-1$
		propertiesFileLabel.setLayoutData(new GridData());
		
		GridData data= new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint= convertWidthInCharsToPixels(30);
		fAccessorClassField= new Text(composite, SWT.SINGLE | SWT.BORDER);
		fAccessorClassField.setLayoutData(data);
		fAccessorClassField.setEditable(false);
		
		data= new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint= convertWidthInCharsToPixels(30);
		fPropertiesFileField= new Text(composite, SWT.SINGLE | SWT.BORDER);
		fPropertiesFileField.setLayoutData(data);
		fPropertiesFileField.setEditable(false);
		
		//new Label(composite, SWT.NONE); // placeholder
		
		Button configure= new Button(accessorComposite, SWT.PUSH);
		configure.setText(NLSUIMessages.getString("ExternalizeWizardPage.configure.button")); //$NON-NLS-1$
		data= new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.VERTICAL_ALIGN_END);
		data.widthHint= SWTUtil.getButtonWidthHint(configure);
		data.heightHint= SWTUtil.getButtonHeightHint(configure);
		configure.setLayoutData(data);

		configure.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				doConfigureButtonPressed();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		
		updateAccessorFieldLabels();
		
	}
	
	private void updateAccessorFieldLabels() {
		String accessorClass= JavaModelUtil.concatenateName(fNLSRefactoring.getAccessorClassPackage().getElementName(), fNLSRefactoring.getAccessorClassName());
		fAccessorClassField.setText(accessorClass);
		fPropertiesFileField.setText(fNLSRefactoring.getPropertyFilePath().makeRelative().toString());
	}
	
	

	private void doConfigureButtonPressed() {
		NLSAccessorConfigurationDialog dialog= new NLSAccessorConfigurationDialog(getShell(), fNLSRefactoring);
		if (dialog.open() == Window.OK) {
			NLSSubstitution.updateSubtitutions(fSubstitutions, getProperties(fNLSRefactoring.getPropertyFileHandle()), fNLSRefactoring.getAccessorClassName());
			fTableViewer.refresh(true);
			updateAccessorFieldLabels();
		}
	}

	private Properties getProperties(IFile propertyFile) {
		Properties props= new Properties();
		try {
			if (propertyFile.exists()) {
				InputStream is= propertyFile.getContents();
				props.load(is);
				is.close();
			}
		} catch (Exception e) {
			// sorry no property         
		}
		return props;
	}

	private void createTableViewer(Composite composite) {
		createTableComposite(composite);

		/*
		 * Feature of CellEditors - double click is ignored.
		 * The workaround is to register my own listener and force the desired 
		 * behavior.
		 */
		fTableViewer= new TableViewer(fTable) {
			protected void hookControl(Control control) {
				super.hookControl(control);
				((Table) control).addMouseListener(new MouseAdapter() {
					public void mouseDoubleClick(MouseEvent e) {
						if (getTable().getSelection().length == 0)
							return;
						TableItem item= getTable().getSelection()[0];
						if (item.getBounds(STATE_PROP).contains(e.x, e.y)) {
							List widgetSel= getSelectionFromWidget();
							if (widgetSel == null || widgetSel.size() != 1)
								return;
							NLSSubstitution substitution= (NLSSubstitution) widgetSel.get(0);
							Integer value= (Integer) getCellModifier().getValue(substitution, PROPERTIES[STATE_PROP]);
							int newValue= MultiStateCellEditor.getNextValue(NLSSubstitution.STATE_COUNT, value.intValue());
							getCellModifier().modify(item, PROPERTIES[STATE_PROP], new Integer(newValue));
						}
					}
				});
			}
		};

		fTableViewer.setUseHashlookup(true);

		final CellEditor[] editors= createCellEditors();
		fTableViewer.setCellEditors(editors);
		fTableViewer.setColumnProperties(PROPERTIES);
		fTableViewer.setCellModifier(new CellModifier());

		fTableViewer.setContentProvider(new IStructuredContentProvider() {
			public Object[] getElements(Object inputElement) {
				return fSubstitutions;
			}
			public void dispose() {
			}
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}
		});
		fTableViewer.addFilter(new ViewerFilter() {
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (!fFilterCheckBox.getSelection()) {
					return true;
				}
				NLSSubstitution curr= (NLSSubstitution) element;
				return (curr.getInitialState() == NLSSubstitution.INTERNALIZED) || (curr.getInitialState() == NLSSubstitution.EXTERNALIZED && curr.getInitialValue() == null);
			}
		});
		

		fTableViewer.setLabelProvider(new NLSSubstitutionLabelProvider());
		fTableViewer.setInput(new Object());

		fTableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				ExternalizeWizardPage.this.selectionChanged(event);
			}
		});
	}

	private void createDefaultExternalization(NLSSubstitution[] substitutions, String defaultPrefix) {
		for (int i= 0; i < substitutions.length; i++) {
			NLSSubstitution substitution= substitutions[i];
			if (substitution.getState() == NLSSubstitution.INTERNALIZED) {
				substitution.setState(NLSSubstitution.EXTERNALIZED);
				substitution.generateKey(substitutions);
			}
		}
	}

	private CellEditor[] createCellEditors() {
		final CellEditor editors[]= new CellEditor[SIZE];
		editors[STATE_PROP]= new MultiStateCellEditor(fTable, NLSSubstitution.STATE_COUNT, NLSSubstitution.DEFAULT);
		editors[KEY_PROP]= new TextCellEditor(fTable);
		editors[VAL_PROP]= new TextCellEditor(fTable);
		return editors;
	}

	private void createSourceViewer(Composite parent) {
		Composite c= new Composite(parent, SWT.NONE);
		c.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout gl= new GridLayout();
		gl.marginHeight= 0;
		gl.marginWidth= 0;
		c.setLayout(gl);

		Label l= new Label(c, SWT.NONE);
		l.setText(NLSUIMessages.getString("ExternalizeWizardPage.context")); //$NON-NLS-1$
		l.setLayoutData(new GridData());

		// source viewer
		JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();
		int styles= SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION;
		IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
		fSourceViewer= new JavaSourceViewer(c, null, null, false, styles, store);
		fSourceViewer.configure(new JavaSourceViewerConfiguration(tools.getColorManager(), store, null, null));
		fSourceViewer.getControl().setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT));

		try {

			String contents= fCu.getBuffer().getContents();
			IDocument document= new Document(contents);
			tools.setupJavaDocumentPartitioner(document);

			fSourceViewer.setDocument(document);
			fSourceViewer.setEditable(false);

			GridData gd= new GridData(GridData.FILL_BOTH);
			gd.heightHint= convertHeightInCharsToPixels(10);
			gd.widthHint= convertWidthInCharsToPixels(40);
			fSourceViewer.getControl().setLayoutData(gd);

		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, NLSUIMessages.getString("ExternalizeWizardPage.title"), NLSUIMessages.getString("ExternalizeWizardPage.exception")); //$NON-NLS-2$ //$NON-NLS-1$
		}
	}

	private void createKeyPrefixField(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		GridLayout gl= new GridLayout();
		gl.numColumns= 2;
		gl.marginWidth= 0;
		composite.setLayout(gl);

		Label l= new Label(composite, SWT.NONE);
		l.setText(NLSUIMessages.getString("ExternalizeWizardPage.common_prefix")); //$NON-NLS-1$
		l.setLayoutData(new GridData());

		fPrefixField= new Text(composite, SWT.SINGLE | SWT.BORDER);
		fPrefixField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		fPrefixField.setText(fNLSRefactoring.getPrefix());
		fPrefixField.selectAll();

		fPrefixField.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				fNLSRefactoring.setPrefix(fPrefixField.getText());
				fTableViewer.refresh(true);
			}
		});
	}

	private void validateKeys() {
		RefactoringStatus status= new RefactoringStatus();
		checkDuplicateKeys(status);
		checkMissingKeys(status);
		setPageComplete(status);
	}

	private void checkDuplicateKeys(RefactoringStatus status) {
		for (int i= 0; i < fSubstitutions.length; i++) {
			NLSSubstitution substitution= fSubstitutions[i];
			if (conflictingKeys(substitution)) {
				status.addFatalError(NLSUIMessages.getString("ExternalizeWizardPage.warning.conflicting")); //$NON-NLS-1$
				return;
			}
		}
	}

	private void checkMissingKeys(RefactoringStatus status) {
		for (int i= 0; i < fSubstitutions.length; i++) {
			NLSSubstitution substitution= fSubstitutions[i];
			if ((substitution.getValue() == null) && (substitution.getKey() != null)) {
				status.addWarning(NLSUIMessages.getString("ExternalizeWizardPage.warning.keymissing")); //$NON-NLS-1$
				return;
			}
		}
	}

	private boolean conflictingKeys(NLSSubstitution substitution) {
		if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
			return substitution.isConflicting(fSubstitutions);
		}
		return false;
	}

	private void createTableComposite(Composite parent) {
		Composite comp= new Composite(parent, SWT.NONE);
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout gl= new GridLayout();
		gl.marginWidth= 0;
		gl.marginHeight= 0;
		comp.setLayout(gl);
		
		Composite labelComp= new Composite(comp, SWT.NONE);
		gl= new GridLayout();
		gl.numColumns= 2;
		gl.marginWidth= 0;
		gl.marginHeight= 0;
		labelComp.setLayout(gl);
		labelComp.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		
		Label l= new Label(labelComp, SWT.NONE);
		l.setText(NLSUIMessages.getString("ExternalizeWizardPage.strings_to_externalize")); //$NON-NLS-1$
		l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		fFilterCheckBox= new Button(labelComp, SWT.CHECK);
		fFilterCheckBox.setText(NLSUIMessages.getString("ExternalizeWizardPage.filter.label")); //$NON-NLS-1$
		fFilterCheckBox.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
		fFilterCheckBox.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				doFilterCheckBoxPressed();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		fFilterCheckBox.setSelection(hasNewSubstitutions());

		Control tableControl= createTable(comp);
		tableControl.setLayoutData(new GridData(GridData.FILL_BOTH));
	}
	
	private boolean hasNewSubstitutions() {
		for (int i= 0; i < fSubstitutions.length; i++) {
			NLSSubstitution curr= fSubstitutions[i];
			if (curr.getInitialState() == NLSSubstitution.INTERNALIZED) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 
	 */
	protected void doFilterCheckBoxPressed() {
		fTableViewer.refresh();
	}

	private Control createTable(Composite parent) {
		Composite c= new Composite(parent, SWT.NONE);
		GridLayout gl= new GridLayout();
		gl.numColumns= 2;
		gl.marginWidth= 0;
		gl.marginHeight= 0;
		c.setLayout(gl);


		fTable= new Table(c, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.FULL_SELECTION | SWT.HIDE_SELECTION | SWT.BORDER);
		GridData tableGD= new GridData(GridData.FILL_BOTH);
		tableGD.heightHint= SWTUtil.getTableHeightHint(fTable, ROW_COUNT);
		//tableGD.widthHint= 40;
		fTable.setLayoutData(tableGD);

		fTable.setLinesVisible(true);

		TableLayout layout= new TableLayout();
		fTable.setLayout(layout);
		fTable.setHeaderVisible(true);

		ColumnLayoutData[] columnLayoutData= new ColumnLayoutData[SIZE];
		columnLayoutData[STATE_PROP]= new ColumnPixelData(20, false);
		columnLayoutData[KEY_PROP]= new ColumnWeightData(40, true);
		columnLayoutData[VAL_PROP]= new ColumnWeightData(40, true);

		for (int i= 0; i < fgTitles.length; i++) {
			TableColumn tc= new TableColumn(fTable, SWT.NONE, i);
			tc.setText(fgTitles[i]);
			layout.addColumnData(columnLayoutData[i]);
			tc.setResizable(columnLayoutData[i].resizable);
		}

		createButtonComposite(c);
		return c;
	}

	private void createButtonComposite(Composite parent) {
		Composite buttonComp= new Composite(parent, SWT.NONE);
		GridLayout gl= new GridLayout();
		gl.marginHeight= 0;
		gl.marginWidth= 0;
		buttonComp.setLayout(gl);
		buttonComp.setLayoutData(new GridData(GridData.FILL_VERTICAL));
		
		SelectionAdapter adapter= new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleButtonPressed(e.widget);
			}
		};

		fExternalizeButton= createTaskButton(buttonComp, NLSUIMessages.getString("ExternalizeWizardPage.Externalize_Selected"), adapter); //$NON-NLS-1$
		fIgnoreButton= createTaskButton(buttonComp, NLSUIMessages.getString("ExternalizeWizardPage.Ignore_Selected"), adapter); //$NON-NLS-1$
		fInternalizeButton= createTaskButton(buttonComp, NLSUIMessages.getString("ExternalizeWizardPage.Internalize_Selected"), adapter); //$NON-NLS-1$

		new Label(buttonComp, SWT.NONE); // separator
		
		fEditButton= createTaskButton(buttonComp, NLSUIMessages.getString("ExternalizeWizardPage.Edit_key_and_value"), adapter); //$NON-NLS-1$
		fRevertButton= createTaskButton(buttonComp, NLSUIMessages.getString("ExternalizeWizardPage.Revert_Selected"), adapter); //$NON-NLS-1$	
		fRenameButton= createTaskButton(buttonComp, NLSUIMessages.getString("ExternalizeWizardPage.Rename_Keys"), adapter); //$NON-NLS-1$

		fEditButton.setEnabled(false);
		fRenameButton.setEnabled(false);
		buttonComp.pack();
	}

	/**
	 * @param widget
	 */
	protected void handleButtonPressed(Widget widget) {
		if (widget == fExternalizeButton) {
			setSelectedTasks(NLSSubstitution.EXTERNALIZED);
		} else if (widget == fIgnoreButton) {
			setSelectedTasks(NLSSubstitution.IGNORED);
		} else if (widget == fInternalizeButton) {
			setSelectedTasks(NLSSubstitution.INTERNALIZED);
		} else if (widget == fEditButton) {
			openEditButton(fTableViewer.getSelection());
		} else if (widget == fRevertButton) {
			revertStateOfSelection();
		} else if (widget == fRenameButton) {
			openRenameDialog();
		}
	}

	/**
	 * 
	 */
	private void openRenameDialog() {
		IStructuredSelection sel= (IStructuredSelection) fTableViewer.getSelection();
		List elementsToRename= getExternalizedElements(sel);
		RenameKeysDialog dialog= new RenameKeysDialog(getShell(), elementsToRename);
		if (dialog.open() == Window.OK) {
			fTableViewer.refresh();
			updateButtonStates((IStructuredSelection) fTableViewer.getSelection());
		}
	}

	private void revertStateOfSelection() {
		List selection= getSelectedTableEntries();
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			substitution.revert();
		}
		fTableViewer.refresh();
		updateButtonStates((IStructuredSelection) fTableViewer.getSelection());
	}
	
	private Button createTaskButton(Composite parent, String label, SelectionAdapter adapter) {
		Button button= new Button(parent, SWT.PUSH);
		button.setText(label); //$NON-NLS-1$
		button.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		SWTUtil.setButtonDimensionHint(button);
		button.addSelectionListener(adapter);
		return button;
	}

	private void openEditButton(ISelection selection) {
		try {
			IStructuredSelection sel= (IStructuredSelection) fTableViewer.getSelection();
			NLSSubstitution substitution= (NLSSubstitution) sel.getFirstElement();
			if (substitution == null) {
				return;
			}
			
			NLSInputDialog dialog= new NLSInputDialog(getShell(), substitution);
			if (dialog.open() == Window.CANCEL)
				return;
			KeyValuePair kvPair= dialog.getResult();
			if (substitution.getState() == NLSSubstitution.EXTERNALIZED) {
				substitution.setKey(kvPair.getKey());
			}
			substitution.setValue(kvPair.getValue());
			validateKeys();
		} finally {
			fTableViewer.refresh();
			fTableViewer.getControl().setFocus();
			fTableViewer.setSelection(selection);
		}
	}

	private List getSelectedTableEntries() {
		ISelection sel= fTableViewer.getSelection();
		if (sel instanceof IStructuredSelection)
			return((IStructuredSelection) sel).toList();
		else
			return Collections.EMPTY_LIST;
	}

	private void setSelectedTasks(int state) {
		Assert.isTrue(state == NLSSubstitution.EXTERNALIZED || state == NLSSubstitution.IGNORED || state == NLSSubstitution.INTERNALIZED);
		List selected= getSelectedTableEntries();
		String[] props= new String[]{PROPERTIES[STATE_PROP]};
		for (Iterator iter= selected.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			substitution.setState(state);
			if ((substitution.getState() == NLSSubstitution.EXTERNALIZED) && substitution.hasStateChanged()) {
				substitution.generateKey(fSubstitutions);
			}
		}
		fTableViewer.update(selected.toArray(), props);
		fTableViewer.getControl().setFocus();
		updateButtonStates((IStructuredSelection) fTableViewer.getSelection());
	}

	private void selectionChanged(SelectionChangedEvent event) {
		IStructuredSelection selection= (IStructuredSelection) event.getSelection();
		updateButtonStates(selection);
		updateSourceView(selection);
	}

	private void updateSourceView(IStructuredSelection selection) {
		NLSSubstitution first= (NLSSubstitution) selection.getFirstElement();
		if (first != null) {
			Region region= first.getNLSElement().getPosition();
			fSourceViewer.setSelectedRange(region.getOffset(), region.getLength());
			fSourceViewer.revealRange(region.getOffset(), region.getLength());
		}
	}

	private void updateButtonStates(IStructuredSelection selection) {
		fExternalizeButton.setEnabled(true);
		fIgnoreButton.setEnabled(true);
		fInternalizeButton.setEnabled(true);
		fRevertButton.setEnabled(true);

		if (containsOnlyElementsOfSameState(NLSSubstitution.EXTERNALIZED, selection)) {
			fExternalizeButton.setEnabled(false);
		}

		if (containsOnlyElementsOfSameState(NLSSubstitution.IGNORED, selection)) {
			fIgnoreButton.setEnabled(false);
		}

		if (containsOnlyElementsOfSameState(NLSSubstitution.INTERNALIZED, selection)) {
			fInternalizeButton.setEnabled(false);
		}

		if (!containsElementsWithChange(selection)) {
			fRevertButton.setEnabled(false);
		}
		
		fRenameButton.setEnabled(getExternalizedElements(selection).size() > 1);
		fEditButton.setEnabled(selection.size() == 1);
	}

	private boolean containsElementsWithChange(IStructuredSelection selection) {
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			if (substitution.hasPropertyFileChange() || substitution.hasSourceChange()) {
				return true;
			}
		}
		return false;
	}
		
	private List getExternalizedElements(IStructuredSelection selection) {
		ArrayList res= new ArrayList();
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			if (substitution.getState() == NLSSubstitution.EXTERNALIZED && !substitution.hasStateChanged()) {
				res.add(substitution);
			}
		}
		return res;
	}

	private boolean containsOnlyElementsOfSameState(int state, IStructuredSelection selection) {
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			NLSSubstitution substitution= (NLSSubstitution) iter.next();
			if (substitution.getState() != state) {
				return false;
			}
		}
		return true;
	}

	public boolean performFinish() {
		return super.performFinish();
	}

	public IWizardPage getNextPage() {
		return super.getNextPage();
	}

	public void dispose() {
		//widgets will be disposed. only need to null'em		
		fPrefixField= null;
		fSourceViewer= null;
		fTable= null;
		fTableViewer= null;
		fEditButton= null;
		super.dispose();
	}
}