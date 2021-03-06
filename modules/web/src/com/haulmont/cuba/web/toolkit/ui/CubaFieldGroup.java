/*
 * Copyright (c) 2008-2016 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.haulmont.cuba.web.toolkit.ui;

import com.haulmont.cuba.web.toolkit.ui.client.fieldgroup.CubaFieldGroupState;
import com.vaadin.ui.Component;
import com.vaadin.ui.Field;
import com.vaadin.ui.Layout;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CubaFieldGroup extends CubaGroupBox {

    protected int currentX = 0;
    protected int currentY = 0;

    protected Map<Object, Field> fields = new HashMap<>();

    public CubaFieldGroup() {
        setLayout(new CubaFieldGroupLayout());
        setSizeUndefined();
    }

    public boolean isBorderVisible() {
        return getState(false).borderVisible;
    }

    public void setBorderVisible(boolean borderVisible) {
        if (getState().borderVisible != borderVisible) {
            getState().borderVisible = borderVisible;
            markAsDirty();
        }
    }

    @Override
    protected CubaFieldGroupState getState() {
        return (CubaFieldGroupState) super.getState();
    }

    @Override
    protected CubaFieldGroupState getState(boolean markAsDirty){
        return (CubaFieldGroupState) super.getState(markAsDirty);
    }

    public void addField(Object propertyId, Field field) {
        addField(propertyId, field, currentX, currentY);
    }

    public void addField(Object propertyId, Field field, int col) {
        addField(propertyId, field, col, currentY);
    }

    public void addField(Object propertyId, Field field, int col, int row) {
        if (col < 0 || col >= getCols() || row < 0 || row >= getRows()) {
            throw new IndexOutOfBoundsException();
        }

        currentX = col;
        currentY = row;

        attachField(propertyId, field);

        if (isReadOnly() != field.isReadOnly() && isReadOnly()) {
            field.setReadOnly(isReadOnly());
        }

        if (row < getRows()) {
            currentY++;
        } else if (col < getCols()) {
            currentX++;
        }
    }

    protected void attachField(Object propertyId, Field field) {
        if (propertyId == null || field == null) {
            return;
        }

        final CubaFieldGroupLayout layout = getLayout();
        Component oldComponent = layout.getComponent(currentX, currentY);
        if (oldComponent != null) {
            layout.removeComponent(oldComponent);
        }

        layout.addComponent(field, currentX, currentY);

        fields.put(propertyId, field);
    }

    public void addCustomField(Object propertyId, CustomFieldGenerator fieldGenerator) {
        addCustomField(propertyId, fieldGenerator, currentX, currentY);
    }

    public void addCustomField(Object propertyId, CustomFieldGenerator fieldGenerator, int col) {
        addCustomField(propertyId, fieldGenerator, col, currentY);
    }

    public void addCustomField(Object propertyId, CustomFieldGenerator fieldGenerator, int col, int row) {
        Field field = fieldGenerator.generateField(propertyId, this);
        addField(propertyId, field, col, row);
    }

    public void setLayout(Layout newLayout) {
        if (newLayout == null) {
            newLayout = new CubaFieldGroupLayout();
        }
        if (newLayout instanceof CubaFieldGroupLayout) {
            super.setContent(newLayout);

            getLayout().setSpacing(true);
        } else {
            throw new IllegalArgumentException("FieldGroup supports only FieldGroupLayout");
        }
    }

    public CubaFieldGroupLayout getLayout() {
        return (CubaFieldGroupLayout) super.getContent();
    }

    public float getColumnExpandRatio(int col) {
        return getLayout().getColumnExpandRatio(col);
    }

    public void setColumnExpandRatio(int col, float ratio) {
        getLayout().setColumnExpandRatio(col, ratio);
    }

    public int getCols() {
        return getLayout().getColumns();
    }

    public void setCols(int cols) {
        getLayout().setColumns(cols);
    }

    public int getRows() {
        return getLayout().getRows();
    }

    public void setRows(int rows) {
        getLayout().setRows(rows);
    }

    public Field getField(Object propertyId) {
        return fields.get(propertyId);
    }

    public interface CustomFieldGenerator extends Serializable {
        com.vaadin.ui.Field generateField(Object propertyId, CubaFieldGroup component);
    }
}