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

package com.haulmont.cuba.gui.components.filter;

import com.haulmont.chile.core.datatypes.Datatype;
import com.haulmont.chile.core.datatypes.Datatypes;
import com.haulmont.chile.core.datatypes.impl.DateTimeDatatype;
import com.haulmont.chile.core.model.Instance;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.cuba.client.ClientConfig;
import com.haulmont.cuba.core.app.DataService;
import com.haulmont.cuba.core.app.PersistenceManagerService;
import com.haulmont.cuba.core.app.dynamicattributes.DynamicAttributesUtils;
import com.haulmont.cuba.core.app.dynamicattributes.PropertyType;
import com.haulmont.cuba.core.entity.CategoryAttribute;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.WindowManager;
import com.haulmont.cuba.gui.WindowManagerProvider;
import com.haulmont.cuba.gui.WindowParams;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.filter.condition.FilterConditionUtils;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.DsBuilder;
import com.haulmont.cuba.gui.data.impl.DatasourceImplementation;
import com.haulmont.cuba.gui.theme.ThemeConstants;
import com.haulmont.cuba.gui.theme.ThemeConstantsManager;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;
import org.dom4j.Element;
import org.springframework.context.annotation.Scope;

import javax.annotation.Nullable;
import javax.persistence.TemporalType;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@org.springframework.stereotype.Component(Param.NAME)
@Scope("prototype")
public class Param {

    public enum Type {
        ENTITY,
        ENUM,
        RUNTIME_ENUM,
        DATATYPE,
        UNARY
    }

    public enum ValueProperty {
        VALUE,
        DEFAULT_VALUE
    }

    public static final String NAME = "cuba_FilterParam";
    public static final String NULL = "NULL";

    protected String name;
    protected Type type;
    protected Class javaClass;
    protected Object value;
    protected Object defaultValue;
    protected String entityWhere;
    protected String entityView;
    protected Datasource datasource;
    protected MetaProperty property;
    protected boolean inExpr;
    protected boolean required;
    protected List<String> runtimeEnum;
    protected UUID categoryAttrId;
    protected Component editComponent;

    protected Messages messages = AppBeans.get(Messages.NAME);
    protected UserSessionSource userSessionSource = AppBeans.get(UserSessionSource.NAME);
    protected ComponentsFactory componentsFactory = AppBeans.get(ComponentsFactory.NAME);
    protected ThemeConstants theme = AppBeans.get(ThemeConstantsManager.class).getConstants();

    protected List<ParamValueChangeListener> listeners = new ArrayList<>();

    public static class Builder {
        private String name;
        private Class javaClass;
        private String entityWhere;
        private String entityView;
        private Datasource dataSource;
        private MetaProperty property;
        private boolean inExpr;
        private boolean required;
        private UUID categoryAttrId;

        private Builder() {
        }

        public static Builder getInstance() {
            return new Builder();
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setJavaClass(Class javaClass) {
            this.javaClass = javaClass;
            return this;
        }

        public Builder setEntityWhere(String entityWhere) {
            this.entityWhere = entityWhere;
            return this;
        }

        public Builder setEntityView(String entityView) {
            this.entityView = entityView;
            return this;
        }

        public Builder setDataSource(Datasource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder setProperty(MetaProperty property) {
            this.property = property;
            return this;
        }

        public Builder setInExpr(boolean inExpr) {
            this.inExpr = inExpr;
            return this;
        }

        public Builder setRequired(boolean required) {
            this.required = required;
            return this;
        }

        public Builder setCategoryAttrId(UUID categoryAttrId) {
            this.categoryAttrId = categoryAttrId;
            return this;
        }

        public Param build() {
            return AppBeans.getPrototype(Param.NAME, this);
        }
    }

    public Param(Builder builder) {
        name = builder.name;
        setJavaClass(builder.javaClass);
        entityWhere = builder.entityWhere;
        entityView = (builder.entityView != null) ? builder.entityView : View.MINIMAL;
        datasource = builder.dataSource;
        property = builder.property;
        inExpr = builder.inExpr;
        required = builder.required;
        categoryAttrId = builder.categoryAttrId;

        if (DynamicAttributesUtils.isDynamicAttribute(builder.property)) {
            CategoryAttribute categoryAttribute = DynamicAttributesUtils.getCategoryAttribute(builder.property);
            if (categoryAttribute.getDataType() == PropertyType.ENUMERATION) {
                type = Type.RUNTIME_ENUM;
            }
        }
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public void setJavaClass(Class javaClass) {
        if (javaClass != null) {
            this.javaClass = javaClass;
            if (Entity.class.isAssignableFrom(javaClass)) {
                type = Type.ENTITY;
            } else if (Enum.class.isAssignableFrom(javaClass)) {
                type = Type.ENUM;
            } else {
                type = Type.DATATYPE;
            }
        } else {
            type = Type.UNARY;
            this.javaClass = Boolean.class;
        }
    }

    public Class getJavaClass() {
        return javaClass;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        setValue(value, true);
    }

    protected void setValue(Object value, boolean updateEditComponent) {
        if (!ObjectUtils.equals(value, this.value)) {
            Object prevValue = this.value;
            this.value = value;
            for (ParamValueChangeListener listener : new ArrayList<>(listeners)) {
                listener.valueChanged(prevValue, value);
            }
            if (updateEditComponent && this.editComponent instanceof Component.HasValue) {
                if (value instanceof Collection && editComponent instanceof TextField) {
                    //if the value type is an array and the editComponent is a textField ('IN' condition for String attribute)
                    //then we should set the string value (not the array) to the text field
                    String caption = new StrBuilder().appendWithSeparators((Collection) value, ",").toString();
                    ((TextField) editComponent).setValue(caption);
                } else {
                    ((Component.HasValue) editComponent).setValue(value);
                }
            }
        }
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
        if (value == null) {
            setValue(defaultValue, true);
        }
    }

    @SuppressWarnings("unchecked")
    public void parseValue(String text) {
        if (NULL.equals(text)) {
            value = null;
            return;
        }

        if (inExpr) {
            if (StringUtils.isBlank(text)) {
                value = new ArrayList<>();
            } else {
                String[] parts = text.split(",");
                List list = new ArrayList(parts.length);
                if (type == Type.ENTITY) {
                    list = loadEntityList(parts);
                } else {
                    for (String part : parts) {
                        Object value = parseSingleValue(part);
                        if (value != null) {
                            list.add(value);
                        }
                    }
                }
                value = list.isEmpty() ? null : list;
            }
        } else {
            value = parseSingleValue(text);
        }
    }

    protected Object parseSingleValue(String text) {
        Object value;
        switch (type) {
            case ENTITY:
                value = loadEntity(text);
                break;

            case ENUM:
                value = Enum.valueOf(javaClass, text);
                break;

            case RUNTIME_ENUM:
            case DATATYPE:
            case UNARY:
                Datatype datatype = Datatypes.getNN(javaClass);
                //hardcode for compatibility with old datatypes
                if (datatype instanceof DateTimeDatatype) {
                    try {
                        value = datatype.parse(text);
                    } catch (ParseException e) {
                        try {
                            value = new SimpleDateFormat("dd/MM/yyyy HH:mm").parse(text);
                        } catch (ParseException exception) {
                            throw new RuntimeException("Can not parse date from string " + text, e);
                        }
                    }
                } else {
                    try {
                        value = datatype.parse(text);
                    } catch (ParseException e) {
                        throw new RuntimeException("Parse exception for string " + text, e);
                    }
                }
                break;

            default:
                throw new IllegalStateException("Param type unknown");
        }
        return value;
    }

    protected List<Entity> loadEntityList(String[] ids) {
        Metadata metadata = AppBeans.get(Metadata.class);
        MetaClass metaClass = metadata.getSession().getClassNN(javaClass);
        LoadContext ctx = new LoadContext(javaClass);
        LoadContext.Query query = ctx.setQueryString("select e from " + metaClass.getName() + " e where e.id in :ids");
        query.setParameter("ids", Arrays.asList(ids));
        DataManager dataManager = AppBeans.get(DataManager.class);
        return dataManager.loadList(ctx);
    }

    protected Object loadEntity(String id) {
        LoadContext ctx = new LoadContext(javaClass).setId(UUID.fromString(id));
        DataService dataService = AppBeans.get(DataService.NAME);
        return dataService.load(ctx);
    }

    public String formatValue(Object value) {
        if (value == null)
            return NULL;

        if (value instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            for (Iterator iterator = ((Collection) value).iterator(); iterator.hasNext(); ) {
                Object v = iterator.next();
                sb.append(formatSingleValue(v));
                if (iterator.hasNext())
                    sb.append(",");
            }
            return sb.toString();
        } else {
            return formatSingleValue(value);
        }
    }

    protected String formatSingleValue(Object v) {
        switch (type) {
            case ENTITY:
                if (v instanceof UUID)
                    return v.toString();
                else if (v instanceof Entity)
                    return ((Entity) v).getId().toString();

            case ENUM:
                return ((Enum) v).name();
            case RUNTIME_ENUM:
            case DATATYPE:
            case UNARY:
                //noinspection unchecked
                Datatype<Object> datatype = Datatypes.getNN(javaClass);
                return datatype.format(v);

            default:
                throw new IllegalStateException("Param type unknown");
        }
    }

    protected String getValueCaption(Object v) {
        if (v == null)
            return null;

        switch (type) {
            case ENTITY:
                if (v instanceof Instance)
                    return ((Instance) v).getInstanceName();
                else
                    return v.toString();

            case ENUM:
                return messages.getMessage((Enum) v);

            case RUNTIME_ENUM:
                return (String) v;

            case DATATYPE:
                return FilterConditionUtils.formatParamValue(this, v);
            case UNARY:
                Datatype datatype = Datatypes.getNN(javaClass);
                return datatype.format(v, userSessionSource.getLocale());

            default:
                throw new IllegalStateException("Param type unknown");
        }
    }

    /**
     * Creates an GUI component for condition parameter.
     *
     * @param valueProperty What value the editor will be connected with: current filter value or default one.
     * @return GUI component for condition parameter.
     */
    public Component createEditComponent(ValueProperty valueProperty) {
        Component component;

        switch (type) {
            case DATATYPE:
                component = createDatatypeField(Datatypes.getNN(javaClass), valueProperty);
                break;
            case ENTITY:
                component = createEntityLookup(valueProperty);
                break;
            case UNARY:
                component = createUnaryField(valueProperty);
                break;
            case ENUM:
                component = createEnumLookup(valueProperty);
                break;
            case RUNTIME_ENUM:
                component = createRuntimeEnumLookup(valueProperty);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported param type: " + type);
        }

        this.editComponent = component;

        return component;
    }

    protected CheckBox createUnaryField(final ValueProperty valueProperty) {
        CheckBox field = componentsFactory.createComponent(CheckBox.class);
        field.addValueChangeListener(e -> {
            Object newValue = BooleanUtils.isTrue((Boolean) e.getValue()) ? true : null;
            _setValue(newValue, valueProperty);
        });
        field.setValue(_getValue(valueProperty));
        field.setAlignment(Component.Alignment.MIDDLE_LEFT);
        return field;
    }

    protected Component createDatatypeField(Datatype datatype, ValueProperty valueProperty) {
        Component component;

        if (String.class.equals(javaClass)) {
            component = createTextField(valueProperty);
        } else if (Date.class.isAssignableFrom(javaClass)) {
            component = createDateField(javaClass, valueProperty);
        } else if (Number.class.isAssignableFrom(javaClass)) {
            component = createNumberField(datatype, valueProperty);
        } else if (Boolean.class.isAssignableFrom(javaClass)) {
            component = createBooleanField(valueProperty);
        } else if (UUID.class.equals(javaClass)) {
            component = createUuidField(valueProperty);
        } else
            throw new UnsupportedOperationException("Unsupported param class: " + javaClass);

        return component;
    }

    protected void _setValue(Object value, ValueProperty valueProperty) {
        switch (valueProperty) {
            case VALUE:
                setValue(value, false);
                break;
            case DEFAULT_VALUE:
                setDefaultValue(value);
                break;
            default:
                throw new IllegalArgumentException("Value property " + valueProperty + " not supported");
        }
    }

    protected Object _getValue(ValueProperty valueProperty) {
        switch (valueProperty) {
            case VALUE:
                return value;
            case DEFAULT_VALUE:
                return defaultValue;
            default:
                throw new IllegalArgumentException("Value property " + valueProperty + " not supported");
        }
    }


    protected Component createTextField(final ValueProperty valueProperty) {
        TextField field = componentsFactory.createComponent(TextField.class);

        field.setWidth(theme.get("cuba.gui.filter.Param.textComponent.width"));

        field.addValueChangeListener(e -> {
            Object paramValue = null;
            if (!StringUtils.isBlank((String) e.getValue())) {
                if (inExpr) {
                    paramValue = new ArrayList<String>();
                    String[] parts = ((String) e.getValue()).split(",");
                    for (String part : parts) {
                        ((List) paramValue).add(part.trim());
                    }
                } else {
                    paramValue = e.getValue();
                }
            }

            if (paramValue instanceof String) {
                Configuration configuration = AppBeans.get(Configuration.NAME);
                if (configuration.getConfig(ClientConfig.class).getGenericFilterTrimParamValues()) {
                    _setValue(StringUtils.trimToNull((String) paramValue), valueProperty);
                } else {
                    _setValue(paramValue, valueProperty);
                }
            } else {
                _setValue(paramValue, valueProperty);
            }
        });

        Object _value = valueProperty == ValueProperty.DEFAULT_VALUE ? defaultValue : value;

        if (_value instanceof List) {
            field.setValue(StringUtils.join((Collection) _value, ","));
        } else if (_value instanceof String) {
            field.setValue(_value);
        } else {
            field.setValue("");
        }

        return field;
    }

    protected Component createDateField(Class javaClass, final ValueProperty valueProperty) {
        if (inExpr) {
            if (property != null) {
                TemporalType tt = (TemporalType) property.getAnnotations().get("temporal");
                if (tt == TemporalType.DATE) {
                    javaClass = java.sql.Date.class;
                }
            }
            final InListParamComponent inListParamComponent = new InListParamComponent(javaClass);
            initListEdit(inListParamComponent, valueProperty);
            return inListParamComponent.getComponent();
        }

        DateField dateField = componentsFactory.createComponent(DateField.class);

        DateField.Resolution resolution;
        String formatStr;
        boolean dateOnly = false;
        if (property != null) {
            TemporalType tt = (TemporalType) property.getAnnotations().get("temporal");
            dateOnly = (tt == TemporalType.DATE);
        } else if (javaClass.equals(java.sql.Date.class)) {
            dateOnly = true;
        }
        Messages messages = AppBeans.get(Messages.NAME);

        if (dateOnly) {
            resolution = com.haulmont.cuba.gui.components.DateField.Resolution.DAY;
            formatStr = messages.getMainMessage("dateFormat");
        } else {
            resolution = com.haulmont.cuba.gui.components.DateField.Resolution.MIN;
            formatStr = messages.getMainMessage("dateTimeFormat");
        }
        dateField.setResolution(resolution);
        dateField.setDateFormat(formatStr);

        dateField.addValueChangeListener(e -> _setValue(e.getValue(), valueProperty));

        dateField.setValue(_getValue(valueProperty));
        return dateField;
    }

    protected Component createNumberField(final Datatype datatype, final ValueProperty valueProperty) {
        TextField field = componentsFactory.createComponent(TextField.class);

        field.addValueChangeListener(e -> {
            if (e.getValue() == null || e.getValue() instanceof Number) {
                _setValue(e.getValue(), valueProperty);
            } else if (e.getValue() instanceof String && !StringUtils.isBlank((String) e.getValue())) {
                UserSessionSource userSessionSource1 = AppBeans.get(UserSessionSource.NAME);

                Object v;
                if (inExpr) {
                    v = new ArrayList();
                    String[] parts = ((String) e.getValue()).split(",");
                    for (String part : parts) {
                        Object p;
                        try {
                            p = datatype.parse(part, userSessionSource1.getLocale());
                        } catch (ParseException ex) {
                            WindowManager wm = AppBeans.get(WindowManagerProvider.class).get();
                            wm.showNotification(messages.getMainMessage("filter.param.numberInvalid"), Frame.NotificationType.ERROR);
                            return;
                        }
                        ((List) v).add(p);
                    }
                } else {
                    try {
                        v = datatype.parse((String) e.getValue(), userSessionSource1.getLocale());
                    } catch (ParseException ex) {
                        WindowManager wm = AppBeans.get(WindowManagerProvider.class).get();
                        wm.showNotification(messages.getMainMessage("Param.numberInvalid"), Frame.NotificationType.ERROR);
                        return;
                    }
                }
                _setValue(v, valueProperty);
            } else if (e.getValue() instanceof String && StringUtils.isBlank((String) e.getValue())) {
                _setValue(null, valueProperty);
            } else {
                throw new IllegalStateException("Invalid value: " + e.getValue());
            }
        });

        UserSessionSource sessionSource = AppBeans.get(UserSessionSource.NAME);
        //noinspection unchecked
        field.setValue(datatype.format(_getValue(valueProperty), sessionSource.getLocale()));
        return field;
    }

    protected Component createBooleanField(final ValueProperty valueProperty) {
        Messages messages = AppBeans.get(Messages.NAME);
        ThemeConstants theme = AppBeans.get(ThemeConstantsManager.class).getConstants();

        LookupField field = componentsFactory.createComponent(LookupField.class);
        field.setWidth(theme.get("cuba.gui.filter.Param.booleanLookup.width"));

        Map<String, Object> values = new HashMap<>();
        values.put(messages.getMainMessage("filter.param.boolean.true"), Boolean.TRUE);
        values.put(messages.getMainMessage("filter.param.boolean.false"), Boolean.FALSE);

        field.setOptionsMap(values);
        field.addValueChangeListener(e -> _setValue(e.getValue(), valueProperty));

        field.setValue(_getValue(valueProperty));
        return field;
    }

    protected Component createUuidField(final ValueProperty valueProperty) {
        TextField field = componentsFactory.createComponent(TextField.class);

        field.addValueChangeListener(e -> {
            String strValue = (String) e.getValue();
            if (strValue == null) {
                _setValue(null, valueProperty);
            } else if ((!StringUtils.isBlank(strValue))) {
                Messages messages1 = AppBeans.get(Messages.NAME);

                if (inExpr) {
                    List list = new ArrayList();
                    String[] parts = strValue.split(",");
                    try {
                        for (String part : parts) {
                            list.add(UUID.fromString(part.trim()));
                        }
                        _setValue(list, valueProperty);
                    } catch (IllegalArgumentException ie) {
                        AppBeans.get(WindowManagerProvider.class).get().showNotification(messages1.getMainMessage("filter.param.uuid.Err"), Frame.NotificationType.TRAY);
                        _setValue(null, valueProperty);
                    }
                } else {
                    try {
                        _setValue(UUID.fromString(strValue), valueProperty);
                    } catch (IllegalArgumentException ie) {
                        AppBeans.get(WindowManagerProvider.class).get().showNotification(messages1.getMainMessage("Param.uuid.Err"), Frame.NotificationType.TRAY);
                    }
                }
            } else if (StringUtils.isBlank(strValue)) {
                _setValue(null, valueProperty);
            } else {
                throw new IllegalStateException("Invalid value: " + strValue);
            }
        });

        Object _value = _getValue(valueProperty);
        if (_value instanceof List) {
            field.setValue(StringUtils.join((Collection) _value, ","));
        } else if (_value instanceof String) {
            field.setValue(_value);
        } else {
            field.setValue("");
        }

        return field;
    }


    protected Component createEntityLookup(final ValueProperty valueProperty) {
        Metadata metadata = AppBeans.get(Metadata.NAME);
        MetaClass metaClass = metadata.getSession().getClassNN(javaClass);

        ThemeConstants theme = AppBeans.get(ThemeConstantsManager.class).getConstants();
        PersistenceManagerService persistenceManager = AppBeans.get(PersistenceManagerService.NAME);
        boolean useLookupScreen = persistenceManager.useLookupScreen(metaClass.getName());

        if (useLookupScreen) {
            if (inExpr) {
                final InListParamComponent inListParamComponent = new InListParamComponent(metaClass);
                initListEdit(inListParamComponent, valueProperty);
                return inListParamComponent.getComponent();
            } else {
                PickerField picker = componentsFactory.createComponent(PickerField.class);
                picker.setMetaClass(metaClass);

                picker.setWidth(theme.get("cuba.gui.filter.Param.textComponent.width"));
                picker.setFrame(datasource.getDsContext().getFrameContext().getFrame());
                picker.addLookupAction();
                picker.addClearAction();

                picker.addValueChangeListener(e -> _setValue(e.getValue(), valueProperty));
                picker.setValue(_getValue(valueProperty));

                return picker;
            }
        } else {
            CollectionDatasource<Entity<Object>, Object> optionsDataSource = createOptionsDataSource(metaClass);

            if (inExpr) {
                final InListParamComponent inListParamComponent = new InListParamComponent(optionsDataSource);
                initListEdit(inListParamComponent, valueProperty);
                return inListParamComponent.getComponent();
            } else {
                final LookupPickerField lookup = componentsFactory.createComponent(LookupPickerField.class);
                lookup.addClearAction();
                lookup.setWidth(theme.get("cuba.gui.filter.Param.textComponent.width"));
                lookup.setOptionsDatasource(optionsDataSource);

                //noinspection unchecked
                optionsDataSource.addCollectionChangeListener(e -> lookup.setValue(null));

                lookup.addValueChangeListener(e -> _setValue(e.getValue(), valueProperty));
                lookup.setValue(_getValue(valueProperty));

                return lookup;
            }
        }
    }

    protected CollectionDatasource<Entity<Object>, Object> createOptionsDataSource(MetaClass metaClass) {
        CollectionDatasource<Entity<Object>, Object> ds = new DsBuilder(datasource.getDsContext())
                .setMetaClass(metaClass)
                .setViewName(entityView)
                .buildCollectionDatasource();

        ds.setRefreshOnComponentValueChange(true);
        ((DatasourceImplementation) ds).initialized();

        if (!StringUtils.isBlank(entityWhere)) {
            QueryTransformer transformer = QueryTransformerFactory.createTransformer(
                    "select e from " + metaClass.getName() + " e");
            transformer.addWhere(entityWhere);
            String q = transformer.getResult();
            ds.setQuery(q);
        }

        if (WindowParams.DISABLE_AUTO_REFRESH.getBool(datasource.getDsContext().getFrameContext())) {
            if (ds instanceof CollectionDatasource.Suspendable)
                ((CollectionDatasource.Suspendable) ds).refreshIfNotSuspended();
            else
                ds.refresh();
        }

        return ds;
    }

    protected Component createEnumLookup(final ValueProperty valueProperty) {
        if (inExpr) {
            final InListParamComponent inListParamComponent = new InListParamComponent(javaClass);
            initListEdit(inListParamComponent, valueProperty);
            return inListParamComponent.getComponent();
        } else {
            LookupField lookup = componentsFactory.createComponent(LookupField.class);
            List options = Arrays.asList(javaClass.getEnumConstants());
            lookup.setOptionsList(options);

            lookup.addValueChangeListener(e -> _setValue(e.getValue(), valueProperty));
            lookup.setValue(_getValue(valueProperty));

            return lookup;
        }
    }

    protected Component createRuntimeEnumLookup(final ValueProperty valueProperty) {
        if (javaClass == Boolean.class) {
            return createBooleanField(valueProperty);
        }
        DataService dataService = AppBeans.get(DataService.NAME);
        LoadContext<CategoryAttribute> context = new LoadContext<>(CategoryAttribute.class);
        LoadContext.Query q = context.setQueryString("select a from sys$CategoryAttribute a where a.id = :id");
        context.setView("_local");
        q.setParameter("id", categoryAttrId);
        CategoryAttribute categoryAttribute = dataService.load(context);
        if (categoryAttribute == null) {
            throw new EntityAccessException();
        }

        runtimeEnum = new LinkedList<>();
        String enumerationString = categoryAttribute.getEnumeration();
        String[] array = StringUtils.split(enumerationString, ',');
        for (String s : array) {
            String trimmedValue = StringUtils.trimToNull(s);
            if (trimmedValue != null) {
                runtimeEnum.add(trimmedValue);
            }
        }

        if (inExpr) {
            final InListParamComponent inListParamComponent = new InListParamComponent(runtimeEnum);
            initListEdit(inListParamComponent, valueProperty);
            return inListParamComponent.getComponent();
        } else {
            LookupField lookup = componentsFactory.createComponent(LookupField.class);
            lookup.setOptionsList(runtimeEnum);

            lookup.addValueChangeListener(e -> {
                _setValue(e.getValue(), valueProperty);
            });

            lookup.setValue(_getValue(valueProperty));

            return lookup;
        }
    }

    protected void initListEdit(InListParamComponent component, ValueProperty valueProperty) {
        component.addValueListener((prevValue, value1) -> _setValue(value1, valueProperty));

        Object _value = _getValue(valueProperty);
        if (_value != null) {
            Map<Object, String> values = new HashMap<>();
            for (Object v : (List) _value) {
                values.put(v, getValueCaption(v));
            }
            component.setValues(values);
        }
    }

    public void toXml(Element element, ValueProperty valueProperty) {
        Element paramElem = element.addElement("param");
        paramElem.addAttribute("name", getName());
        paramElem.addAttribute("javaClass", getJavaClass().getName());
        if (runtimeEnum != null) {
            paramElem.addAttribute("categoryAttrId", categoryAttrId.toString());
        }

        paramElem.setText(formatValue(_getValue(valueProperty)));
    }

    public void addValueChangeListener(ParamValueChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeValueChangeListener(ParamValueChangeListener listener) {
        listeners.remove(listener);
    }

    public MetaProperty getProperty() {
        return property;
    }

    public interface ParamValueChangeListener {

        void valueChanged(@Nullable Object prevValue, @Nullable Object value);
    }
}