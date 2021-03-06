/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.catalogstresstool;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import org.apache.wicket.Session;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.validation.validator.RangeValidator;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.catalog.impl.CoverageInfoImpl;
import org.geoserver.catalog.impl.CoverageStoreInfoImpl;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.FeatureTypeInfoImpl;
import org.geoserver.catalog.impl.LayerInfoImpl;
import org.geoserver.catalog.impl.NamespaceInfoImpl;
import org.geoserver.catalog.impl.WMSLayerInfoImpl;
import org.geoserver.catalog.impl.WMSStoreInfoImpl;
import org.geoserver.catalog.impl.WorkspaceInfoImpl;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.ows.util.OwsUtils;
import org.geoserver.security.SecureCatalogImpl;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.ToolPage;
import org.opengis.filter.Filter;

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class CatalogStressTester extends GeoServerSecuredPage {

    DropDownChoice<Tuple> workspace;

    DropDownChoice<Tuple> store;

    DropDownChoice<Tuple> resourceAndLayer;

    TextField<Integer> duplicateCount;

    TextField<String> sufix;

    Label progress;

    AjaxButton startLink;

    /**
     * DropDown choice model object becuase dbconfig freaks out if using the CatalogInfo objects
     * directly
     * 
     */
    private static final class Tuple implements Serializable, Comparable<Tuple> {
        private static final long serialVersionUID = 1L;

        final String id, name;

        public Tuple(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public int compareTo(Tuple o) {
            return name.compareTo(o.name);
        }
    }

    private static class TupleChoiceRenderer implements IChoiceRenderer<Tuple> {
        private static final long serialVersionUID = 1L;

        @Override
        public Object getDisplayValue(Tuple object) {
            return object.name;
        }

        @Override
        public String getIdValue(Tuple object, int index) {
            return object.id;
        }
    }

    public CatalogStressTester() {
        super();
        setDefaultModel(new Model());
        Form form = new Form("form", new Model());
        add(form);

        IModel<List<Tuple>> wsModel = new LoadableDetachableModel<List<Tuple>>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<Tuple> load() {
                Catalog catalog = GeoServerApplication.get().getCatalog();
                Filter filter = Predicates.acceptAll();
                CloseableIterator<WorkspaceInfo> list = catalog.list(WorkspaceInfo.class, filter,
                        null, 4000, null);
                List<Tuple> workspaces;
                try {
                    workspaces = Lists.newArrayList(Iterators.transform(list,
                            new Function<WorkspaceInfo, Tuple>() {
                                @Override
                                public Tuple apply(WorkspaceInfo input) {
                                    return new Tuple(input.getId(), input.getName());
                                }
                            }));
                } finally {
                    list.close();
                }
                Collections.sort(workspaces);
                return workspaces;
            }
        };
        workspace = new DropDownChoice<Tuple>("workspace", new Model<Tuple>(), wsModel,
                new TupleChoiceRenderer());
        workspace.setNullValid(true);

        workspace.setOutputMarkupId(true);
        workspace.setRequired(true);
        form.add(workspace);
        workspace.add(new OnChangeAjaxBehavior() {
            private static final long serialVersionUID = -5613056077847641106L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                target.addComponent(store);
                target.addComponent(resourceAndLayer);
            }
        });

        IModel<List<Tuple>> storesModel = new LoadableDetachableModel<List<Tuple>>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<Tuple> load() {
                Catalog catalog = GeoServerApplication.get().getCatalog();
                Tuple ws = workspace.getModelObject();
                if (ws == null) {
                    return Lists.newArrayList();
                }
                Filter filter = Predicates.equal("workspace.id", ws.id);
                int limit = 100;
                CloseableIterator<StoreInfo> iter = catalog.list(StoreInfo.class, filter, null,
                        limit, null);

                List<Tuple> stores;
                try {
                    stores = Lists.newArrayList(Iterators.transform(iter,
                            new Function<StoreInfo, Tuple>() {

                                @Override
                                public Tuple apply(StoreInfo input) {
                                    return new Tuple(input.getId(), input.getName());
                                }
                            }));
                } finally {
                    iter.close();
                }
                Collections.sort(stores);
                return stores;
            }
        };

        store = new DropDownChoice<Tuple>("store", new Model<Tuple>(), storesModel,
                new TupleChoiceRenderer());
        store.setNullValid(true);

        store.setOutputMarkupId(true);
        store.add(new OnChangeAjaxBehavior() {
            private static final long serialVersionUID = -5333344688588590014L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                target.addComponent(resourceAndLayer);
            }
        });
        form.add(store);

        IModel<List<Tuple>> resourcesModel = new LoadableDetachableModel<List<Tuple>>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<Tuple> load() {
                Catalog catalog = getCatalog();
                Tuple storeInfo = store.getModelObject();
                if (storeInfo == null) {
                    return Lists.newArrayList();
                }
                Integer limit = 100;
                Filter filter = Predicates.equal("store.id", storeInfo.id);
                CloseableIterator<ResourceInfo> iter = catalog.list(ResourceInfo.class, filter,
                        null, limit, null);

                List<Tuple> resources;
                try {
                    resources = Lists.newArrayList(Iterators.transform(iter,
                            new Function<ResourceInfo, Tuple>() {
                                @Override
                                public Tuple apply(ResourceInfo input) {
                                    return new Tuple(input.getId(), input.getName());
                                }
                            }));
                } finally {
                    iter.close();
                }
                Collections.sort(resources);
                return resources;
            }
        };

        resourceAndLayer = new DropDownChoice<Tuple>("resourceAndLayer", new Model<Tuple>(),
                resourcesModel, new TupleChoiceRenderer());
        resourceAndLayer.setNullValid(true);

        resourceAndLayer.setOutputMarkupId(true);
        form.add(resourceAndLayer);

        duplicateCount = new TextField<Integer>("duplicateCount", new Model<Integer>(100),
                Integer.class);
        duplicateCount.setRequired(true);
        duplicateCount.add(new RangeValidator<Integer>(1, 100000));
        form.add(duplicateCount);

        sufix = new TextField<String>("sufix", new Model<String>("copy-"));
        sufix.setRequired(true);
        form.add(sufix);

        progress = new Label("progress", new Model<String>("0/0"));
        progress.setOutputMarkupId(true);
        form.add(progress);

        form.add(new AjaxButton("cancel") {
            private static final long serialVersionUID = 5767430648099432407L;

            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                setResponsePage(ToolPage.class);
            }
        });

        startLink = new AjaxButton("submit", form) {
            private static final long serialVersionUID = -4087484089208211355L;

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                progress.setDefaultModelObject("");
                startLink.setVisible(false);
                target.addComponent(startLink);
                target.addComponent(progress);
                try {
                    startCopy(target, form);
                } catch (Exception e) {
                    form.error(e.getMessage());
                    target.addComponent(form);
                } finally {
                    startLink.setVisible(true);
                    target.addComponent(startLink);
                    target.addComponent(progress);
                }
            }

        };
        form.add(startLink);
        startLink.setOutputMarkupId(true);
    }

    private void startCopy(AjaxRequestTarget target, Form<?> form) {
        Session.get().getFeedbackMessages().clear();
        target.addComponent(getFeedbackPanel());

        final int numCopies = duplicateCount.getModelObject();
        final String s = sufix.getModelObject();

        LayerInfo layer = null;

        CatalogInfo original;
        {
            Tuple modelObject = resourceAndLayer.getModelObject();
            if (modelObject != null) {
                original = getCatalog().getResource(modelObject.id, ResourceInfo.class);
                List<LayerInfo> layers = getCatalog().getLayers((ResourceInfo) original);
                if (!layers.isEmpty()) {
                    layer = layers.get(0);
                }

            } else {
                modelObject = store.getModelObject();
                if (modelObject != null) {
                    original = getCatalog().getStore(modelObject.id, StoreInfo.class);
                } else {
                    modelObject = workspace.getModelObject();
                    if (modelObject != null) {
                        original = getCatalog().getWorkspace(modelObject.id);
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }

        System.err.println("Creating " + numCopies + " copies of " + original + " with sufix " + s);

        final Catalog catalog = getCatalog();

        final Class<? extends CatalogInfo> clazz = interfaceOf(original);

        Stopwatch globalTime = new Stopwatch();
        Stopwatch sw = new Stopwatch();
        sw.start();
        final int padLength = (int) Math.ceil(Math.log10(numCopies));
        for (int curr = 0; curr < numCopies; curr++) {
            String paddedIndex = Strings.padStart(String.valueOf(curr), padLength, '0');
            String nameSuffix = s + paddedIndex;
            copyOne(catalog, original, (Class<CatalogInfo>) clazz, layer, nameSuffix, globalTime);
            if ((curr + 1) % 100 == 0) {
                sw.stop();
                System.out.printf("inserted %s so far in %s (last 100 in %s)\n", (curr + 1),
                        globalTime, sw);
                sw.reset();
                sw.start();
            }
        }

        System.out.println("added " + numCopies + " copies of " + original + " in " + globalTime);

        progress.setDefaultModelObject("Inserted " + numCopies + " copies of " + original + "in "
                + globalTime);
        target.addComponent(progress);
    }

    private Class<? extends CatalogInfo> interfaceOf(CatalogInfo original) {
        Class<?>[] interfaces = { LayerGroupInfo.class, LayerInfo.class, NamespaceInfo.class,
                WorkspaceInfo.class, StyleInfo.class, CoverageStoreInfo.class, DataStoreInfo.class,
                WMSStoreInfo.class, CoverageInfo.class, FeatureTypeInfo.class, WMSLayerInfo.class };
        for (Class c : interfaces) {
            if (c.isAssignableFrom(original.getClass())) {
                return c;
            }
        }
        throw new IllegalArgumentException();
    }

    private void copyOne(Catalog catalog, final CatalogInfo original,
            final Class<CatalogInfo> clazz, final LayerInfo layer, final String nameSuffix,
            final Stopwatch sw) {

        CatalogInfo prototype = prototype(original, catalog);

        try {
            OwsUtils.set(prototype, "id", null);
            OwsUtils.copy(clazz.cast(original), clazz.cast(prototype), clazz);
            final String newName = OwsUtils.get(prototype, "name") + nameSuffix;
            OwsUtils.set(prototype, "name", newName);
            if (prototype instanceof WorkspaceInfo) {

                sw.start();
                catalog.add((WorkspaceInfo) prototype);
                sw.stop();

                String originalWsName = ((WorkspaceInfo) original).getName();
                NamespaceInfo ns = catalog.getNamespaceByPrefix(originalWsName);
                NamespaceInfoImpl ns2 = new NamespaceInfoImpl();
                ns2.setPrefix(newName);
                ns2.setURI(ns.getURI() + newName);
                sw.start();
                catalog.add(ns2);
                sw.stop();

            } else if (prototype instanceof StoreInfo) {

                sw.start();
                catalog.add((StoreInfo) prototype);
                sw.stop();

            } else if (prototype instanceof ResourceInfo) {
                ((ResourceInfo) prototype).setNativeName(((ResourceInfo) original).getNativeName());
                ((ResourceInfo) prototype).setName(newName);
                sw.start();
                catalog.add((ResourceInfo) prototype);
                sw.stop();

                String id = prototype.getId();
                // prototype = catalog.getResource(id, ResourceInfo.class);

                if (layer == null) {
                    return;
                }
                LayerInfoImpl layerCopy;
                {
                    layerCopy = new LayerInfoImpl();
                    layerCopy.setResource((ResourceInfo) original);
                    OwsUtils.copy(LayerInfo.class.cast(layer), layerCopy, LayerInfo.class);
                    layerCopy.setResource((ResourceInfo) prototype);
                    layerCopy.setId(null);
                }
                sw.start();
                catalog.add(layerCopy);
                sw.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private CatalogInfo prototype(CatalogInfo original, Catalog catalog) {
        CatalogInfo prototype;
        if (original instanceof WorkspaceInfo) {
            prototype = new WorkspaceInfoImpl();
        } else if (original instanceof DataStoreInfo) {
            prototype = new DataStoreInfoImpl(catalog);
        } else if (original instanceof CoverageStoreInfo) {
            prototype = new CoverageStoreInfoImpl(catalog);
        } else if (original instanceof WMSStoreInfo) {
            prototype = new WMSStoreInfoImpl((CatalogImpl) SecureCatalogImpl.unwrap(catalog));
        } else if (original instanceof FeatureTypeInfo) {
            prototype = new FeatureTypeInfoImpl(catalog);
        } else if (original instanceof CoverageInfo) {
            prototype = new CoverageInfoImpl(catalog);
        } else if (original instanceof WMSLayerInfo) {
            prototype = new WMSLayerInfoImpl((CatalogImpl) SecureCatalogImpl.unwrap(catalog));
        } else {
            throw new IllegalArgumentException(original.toString());
        }
        return prototype;
    }

}
