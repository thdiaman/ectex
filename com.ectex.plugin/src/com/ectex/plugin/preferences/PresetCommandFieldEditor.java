package com.ectex.plugin.preferences;

import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;

/**
 * A drop-down list of common commands that can also be edited: pick "pdflatex", "xelatex"..., or type any
 * other command or a full path. An empty value is replaced by the default when stored.
 */
class PresetCommandFieldEditor extends FieldEditor {

	private final String[] presets;
	private Combo combo;

	PresetCommandFieldEditor(String name, String labelText, String[] presets, Composite parent) {
		// Not the (name, label, parent) constructor: it builds the controls before the fields above are set.
		init(name, labelText);
		this.presets = presets;
		createControl(parent);
	}

	@Override
	protected void adjustForNumColumns(int numColumns) {
		((GridData) combo.getLayoutData()).horizontalSpan = numColumns - 1;
	}

	@Override
	protected void doFillIntoGrid(Composite parent, int numColumns) {
		getLabelControl(parent);
		combo = new Combo(parent, SWT.DROP_DOWN);
		combo.setItems(presets);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.horizontalSpan = numColumns - 1;
		combo.setLayoutData(data);
		combo.addModifyListener(e -> setPresentsDefaultValue(false));
	}

	@Override
	protected void doLoad() {
		combo.setText(getPreferenceStore().getString(getPreferenceName()));
	}

	@Override
	protected void doLoadDefault() {
		combo.setText(getPreferenceStore().getDefaultString(getPreferenceName()));
		setPresentsDefaultValue(false);
	}

	@Override
	protected void doStore() {
		String value = combo.getText().trim();
		if (value.isEmpty()) {
			value = getPreferenceStore().getDefaultString(getPreferenceName());
		}
		getPreferenceStore().setValue(getPreferenceName(), value);
	}

	@Override
	public int getNumberOfControls() {
		return 2;
	}
}
