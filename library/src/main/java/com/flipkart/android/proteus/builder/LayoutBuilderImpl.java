/*
 * Copyright 2016 Flipkart Internet Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flipkart.android.proteus.builder;

import android.os.Debug;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;

import com.flipkart.android.proteus.parser.ProtoLayoutHandler;
import com.flipkart.android.proteus.providers.Attribute;
import com.flipkart.android.proteus.providers.Attributes;
import com.flipkart.android.proteus.providers.Data;
import com.flipkart.android.proteus.providers.Layout;
import com.flipkart.android.proteus.toolbox.BitmapLoader;
import com.flipkart.android.proteus.toolbox.IdGenerator;
import com.flipkart.android.proteus.toolbox.ProteusConstants;
import com.flipkart.android.proteus.toolbox.Styles;
import com.flipkart.android.proteus.toolbox.Utils;
import com.flipkart.android.proteus.view.ProteusView;
import com.flipkart.android.proteus.view.manager.ProteusViewManager;
import com.flipkart.android.proteus.view.manager.ProteusViewManagerImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * A layout builder which can parse json to construct an android view out of it. It uses the
 * registered handlers to convert the json string to a view and then assign attributes.
 */
public class LayoutBuilderImpl implements ProtoLayoutBuilder {

    private static Logger logger = LoggerFactory.getLogger(LayoutBuilderImpl.class);
    @Nullable
    protected ProtoLayoutBuilderCallback listener;
    private HashMap<String, ProtoLayoutHandler> layoutHandlers = new HashMap<>();
    @Nullable
    private BitmapLoader bitmapLoader;
    private boolean isSynchronousRendering = false;
    private IdGenerator idGenerator;

    public LayoutBuilderImpl(@NonNull IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public void registerHandler(String type, ProtoLayoutHandler handler) {
        handler.prepareAttributeHandlers();
        layoutHandlers.put(type, handler);
    }

    @Override
    public void unregisterHandler(String type) {
        layoutHandlers.remove(type);
    }

    @Override
    @Nullable
    public ProtoLayoutHandler getHandler(String type) {
        return layoutHandlers.get(type);
    }

    @Override
    @Nullable
    public ProteusView build(ViewGroup parent, Layout layout, Data data, int index, Styles styles) {
        String type = layout.getType();

        if (type == null) {
            throw new IllegalArgumentException("'type' missing in layout: " + layout.toString());
        }

        ProtoLayoutHandler handler = layoutHandlers.get(type);
        if (handler == null) {
            return onUnknownViewEncountered(type, parent, layout, data, index, styles);
        }

        /**
         * View creation.
         */
        final ProteusView view;

        onBeforeCreateView(handler, parent, layout, data, index, styles);
        view = createView(handler, parent, layout, data, index, styles);
        onAfterCreateView(handler, view, parent, layout, data, index, styles);

        ProteusViewManager viewManager = createViewManager(handler, parent, layout, data, index, styles);
        viewManager.setView((View) view);
        view.setViewManager(viewManager);

        /**
         * Parsing each attribute and setting it on the view.
         */


        List<Attribute> attributesList = layout.getAttributeList();
        if (null != attributesList) {
            for (Attribute attributes : attributesList) {
                handleAttribute(handler, view, attributes);
            }
        }

        /**
         * Process the children.
         */

        List<Layout> childLayoutList = layout.getChildren();
        if (null != childLayoutList && childLayoutList.size() > 0) {
            for (Layout childLayout : childLayoutList) {
                handleChildren(handler, view, childLayout, this);
            }
        }

        return view;
    }

    protected void onBeforeCreateView(ProtoLayoutHandler handler, ViewGroup parent, Layout layout, Data data, int index, Styles styles) {
        handler.onBeforeCreateView(parent, layout, data, styles, index);
    }

    protected ProteusView createView(ProtoLayoutHandler handler, ViewGroup parent, Layout layout, Data data, int index, Styles styles) {
        return handler.createView(parent, layout, data, styles, index);
    }

    protected void onAfterCreateView(ProtoLayoutHandler handler, ProteusView view, ViewGroup parent, Layout layout, Data data, int index, Styles styles) {
        //noinspection unchecked
        handler.onAfterCreateView((View) view, parent, layout, data, styles, index);
    }

    protected ProteusViewManager createViewManager(ProtoLayoutHandler handler, View parent, Layout layout, Data data, int index, Styles styles) {
        if (ProteusConstants.isLoggingEnabled()) {
            logger.debug("ProteusView created with " + layout.getType());
        }

        ProteusViewManagerImpl viewManager = new ProteusViewManagerImpl();
//        DataContext dataContext = new DataContext();
//        dataContext.setData(data);
//        dataContext.setIndex(index);
//
//        viewManager.setLayout(layout);
//        viewManager.setDataContext(dataContext);
//        viewManager.setStyles(styles);
//        viewManager.setLayoutBuilder(this);
//        viewManager.setLayoutHandler(handler);

        return viewManager;
    }

    protected void handleChildren(ProtoLayoutHandler handler, ProteusView view, Layout childLayout, ProtoLayoutBuilder layoutBuilder) {
        if (ProteusConstants.isLoggingEnabled()) {
            logger.debug("Parsing children for view with " + Utils.getLayoutIdentifier(view.getViewManager().getLayout()));
        }

        handler.handleChildren(view, childLayout, layoutBuilder);
    }

    public boolean handleAttribute(ProtoLayoutHandler handler, ProteusView view, Attribute attribute) {
        if (ProteusConstants.isLoggingEnabled()) {
            //logger.debug("Handle '" + attributes + "' : " + attributes.getValue() + " for view with " + Utils.getLayoutIdentifier(view.getViewManager().getLayout()));
        }
        //noinspection unchecked
        return handler.handleAttribute((View) view, attribute);
    }

    protected void onUnknownAttributeEncountered(Attributes attributes, ProteusView view) {
        if (listener != null) {
            listener.onUnknownAttribute(attributes, view);
        }
    }

    @Nullable
    protected ProteusView onUnknownViewEncountered(String type, ViewGroup parent, Layout layout, Data data, int index, Styles styles) {
        if (ProteusConstants.isLoggingEnabled()) {
            logger.debug("No LayoutHandler for: " + type);
        }
        if (listener != null) {
            return listener.onUnknownViewType(type, parent, layout, data, index, styles);
        }
        return null;
    }

    @Override
    public int getUniqueViewId(String id) {
        return idGenerator.getUnique(id);
    }

    @Nullable
    @Override
    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    @Nullable
    @Override
    public ProtoLayoutBuilderCallback getListener() {
        return listener;
    }

    @Override
    public void setListener(@Nullable ProtoLayoutBuilderCallback listener) {
        this.listener = listener;
    }

    @Override
    public BitmapLoader getNetworkDrawableHelper() {
        return bitmapLoader;
    }

    @Override
    public void setBitmapLoader(@Nullable BitmapLoader bitmapLoader) {
        this.bitmapLoader = bitmapLoader;
    }

    @Override
    public boolean isSynchronousRendering() {
        return isSynchronousRendering;
    }

    @Override
    public void setSynchronousRendering(boolean isSynchronousRendering) {
        this.isSynchronousRendering = isSynchronousRendering;
    }
}
