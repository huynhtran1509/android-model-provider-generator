/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 * 
 * Copyright 2012 Benoit 'BoD' Lubek (BoD@JRAF.org).  All Rights Reserved.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.willowtreeapps.androidcontentprovidergenerator;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.beust.jcommander.JCommander;
import com.willowtreeapps.androidcontentprovidergenerator.model.Constraint;
import com.willowtreeapps.androidcontentprovidergenerator.model.Entity;
import com.willowtreeapps.androidcontentprovidergenerator.model.Field;
import com.willowtreeapps.androidcontentprovidergenerator.model.Model;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class Main {
    private static String TAG = Constants.TAG + Main.class.getSimpleName();

    private static String FILE_CONFIG = "_config.json";

    public static class Json {
        public static final String TOOL_VERSION = "toolVersion";
        public static final String PROJECT_PACKAGE_ID = "projectPackageId";
        public static final String PROVIDER_JAVA_PACKAGE = "providerJavaPackage";
        public static final String PROVIDER_CLASS_NAME = "providerClassName";
        public static final String SQLITE_HELPER_CLASS_NAME = "sqliteHelperClassName";
        public static final String AUTHORITY = "authority";
        public static final String DATABASE_FILE_NAME = "databaseFileName";
        public static final String ENABLE_FOREIGN_KEY = "enableForeignKeys";
        public static final String PROJECT_BASE_URL = "projectBaseUrl";

        public static final String GENERATE_PROVIDER = "generateProvider";
        public static final String GENERATE_MODELS = "generateModels";
        public static final String GENERATE_VIEWS = "generateViews";
        public static final String GENERATE_API = "generateApi";
        public static final String GENERATE_FRAGMENT = "generateFragments";
    }

    private Configuration mFreemarkerConfig;
    private JSONObject mConfig;

    private Configuration getFreeMarkerConfig() {
        if (mFreemarkerConfig == null) {
            mFreemarkerConfig = new Configuration();
            mFreemarkerConfig.setClassForTemplateLoading(getClass(), "");
            mFreemarkerConfig.setObjectWrapper(new DefaultObjectWrapper());
        }
        return mFreemarkerConfig;
    }

    private void loadModel(File inputDir) throws IOException, JSONException {
        File[] entityFiles = inputDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return !pathname.getName().startsWith("_") && pathname.getName().endsWith(".json");
            }
        });
        for (File entityFile : entityFiles) {
            if (Config.LOGD) Log.d(TAG, entityFile.getCanonicalPath());
            String entityName = FilenameUtils.getBaseName(entityFile.getCanonicalPath());
            if (Config.LOGD) Log.d(TAG, "entityName=" + entityName);
            Entity entity = new Entity(entityName);
            String fileContents = FileUtils.readFileToString(entityFile);
            JSONObject entityJson = new JSONObject(fileContents);
            entity.setUrl(entityJson.optString("urlPath"));
            // Fields
            JSONArray fieldsJson = entityJson.getJSONArray("fields");
            int len = fieldsJson.length();
            for (int i = 0; i < len; i++) {
                JSONObject fieldJson = fieldsJson.getJSONObject(i);
                if (Config.LOGD) Log.d(TAG, "fieldJson=" + fieldJson);
                String name = fieldJson.getString(Field.Json.NAME);
                String serializedName = fieldJson.optString(Field.Json.SERIALIZED_NAME);
                String type = fieldJson.getString(Field.Json.TYPE);
                boolean isIndex = fieldJson.optBoolean(Field.Json.INDEX, false);
                boolean isNullable = fieldJson.optBoolean(Field.Json.NULLABLE, true);
                String defaultValue = fieldJson.optString(Field.Json.DEFAULT_VALUE);
                String enumName = fieldJson.optString(Field.Json.ENUM_NAME);
                JSONArray enumValuesJson = fieldJson.optJSONArray(Field.Json.ENUM_VALUES);
                List<String> enumValues = new ArrayList<String>();
                if (enumValuesJson != null) {
                    int enumLen = enumValuesJson.length();
                    for (int j = 0; j < enumLen; j++) {
                        String valueName = enumValuesJson.getString(j);
                        enumValues.add(valueName);
                    }
                }
                Field field = new Field(name, serializedName, type, isIndex, isNullable, defaultValue, enumName, enumValues);
                entity.addField(field);
            }

            // Constraints (optional)
            JSONArray constraintsJson = entityJson.optJSONArray("constraints");
            if (constraintsJson != null) {
                len = constraintsJson.length();
                for (int i = 0; i < len; i++) {
                    JSONObject constraintJson = constraintsJson.getJSONObject(i);
                    if (Config.LOGD) Log.d(TAG, "constraintJson=" + constraintJson);
                    String name = constraintJson.getString(Constraint.Json.NAME);
                    String definition = constraintJson.getString(Constraint.Json.DEFINITION);
                    Constraint constraint = new Constraint(name, definition);
                    entity.addConstraint(constraint);
                }
            }

            // QueryParams (optional)
            JSONArray paramsJson = entityJson.optJSONArray("queryParams");
            if (paramsJson != null) {
                len = paramsJson.length();
                for (int i = 0; i < len; i++) {
                    JSONObject constraintJson = paramsJson.getJSONObject(i);
                    String name = constraintJson.getString(Constraint.Json.NAME);
                    entity.addQueryParam(name);
                }
            }

            Model.get().addEntity(entity);
        }
        // Header (optional)
        File headerFile = new File(inputDir, "header.txt");
        if (headerFile.exists()) {
            String header = FileUtils.readFileToString(headerFile).trim();
            Model.get().setHeader(header);
        }
        if (Config.LOGD) Log.d(TAG, Model.get().toString());
    }

    private JSONObject getConfig(File inputDir) throws IOException, JSONException {
        if (mConfig == null) {
            File configFile = new File(inputDir, FILE_CONFIG);
            String fileContents = FileUtils.readFileToString(configFile);
            mConfig = new JSONObject(fileContents);
        }

        validateConfig();

        return mConfig;
    }

    private void validateConfig() {
        // Ensure the input files are compatible with this version of the tool
        String configVersion;
        try {
            configVersion = mConfig.getString(Json.TOOL_VERSION);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Could not find 'toolVersion' field in _config.json, which is mandatory and must be equals to '"
                    + Constants.VERSION + "'.");
        }
        if (!configVersion.equals(Constants.VERSION)) {
            throw new IllegalArgumentException("Invalid 'toolVersion' value in _config.json: found '" + configVersion + "' but expected '" + Constants.VERSION
                    + "'.");
        }

        // Ensure mandatory fields are present
        if(mConfig.optBoolean(Json.GENERATE_PROVIDER, true)){
            ensureString(Json.PROVIDER_JAVA_PACKAGE);
            ensureString(Json.PROVIDER_CLASS_NAME);
            ensureString(Json.SQLITE_HELPER_CLASS_NAME);
            ensureString(Json.AUTHORITY);
            ensureString(Json.DATABASE_FILE_NAME);
            ensureBoolean(Json.ENABLE_FOREIGN_KEY);
        }
        if(mConfig.optBoolean(Json.GENERATE_API, true)){
            ensureString(Json.PROJECT_BASE_URL);
        }
        ensureString(Json.PROJECT_PACKAGE_ID);

    }

    private void ensureString(String field) {
        try {
            mConfig.getString(field);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Could not find '" + field + "' field in _config.json, which is mandatory and must be a string.");
        }
    }

    private void ensureBoolean(String field) {
        try {
            mConfig.getBoolean(field);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Could not find '" + field + "' field in _config.json, which is mandatory and must be a boolean.");
        }
    }

    private void generateColumns(Arguments arguments) throws IOException, JSONException, TemplateException {
        Template template = getFreeMarkerConfig().getTemplate("columns.ftl");
        JSONObject config = getConfig(arguments.inputDir);
        String providerJavaPackage = config.getString(Json.PROVIDER_JAVA_PACKAGE);

        File providerDir = new File(arguments.outputDir, providerJavaPackage.replace('.', '/'));
        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", getConfig(arguments.inputDir));
        root.put("header", Model.get().getHeader());

        // Entities
        for (Entity entity : Model.get().getEntities()) {
            File outputDir = new File(providerDir, entity.getNameLowerCase());
            outputDir.mkdirs();
            File outputFile = new File(outputDir, entity.getNameCamelCase() + "Columns.java");
            Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));

            root.put("entity", entity);

            template.process(root, out);
            IOUtils.closeQuietly(out);
        }
    }

    private void generateWrappers(Arguments arguments) throws IOException, JSONException, TemplateException {
        JSONObject config = getConfig(arguments.inputDir);
        String providerJavaPackage = config.getString(Json.PROVIDER_JAVA_PACKAGE);
        File providerDir = new File(arguments.outputDir, providerJavaPackage.replace('.', '/'));
        File baseClassesDir = new File(providerDir, "base");
        baseClassesDir.mkdirs();

        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", getConfig(arguments.inputDir));
        root.put("header", Model.get().getHeader());

        // AbstractCursor
        Template template = getFreeMarkerConfig().getTemplate("abstractcursor.ftl");
        File outputFile = new File(baseClassesDir, "AbstractCursor.java");
        Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));
        template.process(root, out);
        IOUtils.closeQuietly(out);

        // AbstractContentValuesWrapper
        template = getFreeMarkerConfig().getTemplate("abstractcontentvalues.ftl");
        outputFile = new File(baseClassesDir, "AbstractContentValues.java");
        out = new OutputStreamWriter(new FileOutputStream(outputFile));
        template.process(root, out);
        IOUtils.closeQuietly(out);

        // AbstractSelection
        template = getFreeMarkerConfig().getTemplate("abstractselection.ftl");
        outputFile = new File(baseClassesDir, "AbstractSelection.java");
        out = new OutputStreamWriter(new FileOutputStream(outputFile));
        template.process(root, out);
        IOUtils.closeQuietly(out);



        // Entities
        for (Entity entity : Model.get().getEntities()) {
            File entityDir = new File(providerDir, entity.getNameLowerCase());
            entityDir.mkdirs();

            // Cursor wrapper
            outputFile = new File(entityDir, entity.getNameCamelCase() + "Cursor.java");
            out = new OutputStreamWriter(new FileOutputStream(outputFile));
            root.put("entity", entity);
            template = getFreeMarkerConfig().getTemplate("cursor.ftl");
            template.process(root, out);
            IOUtils.closeQuietly(out);

            // ContentValues wrapper
            outputFile = new File(entityDir, entity.getNameCamelCase() + "ContentValues.java");
            out = new OutputStreamWriter(new FileOutputStream(outputFile));
            root.put("entity", entity);
            template = getFreeMarkerConfig().getTemplate("contentvalues.ftl");
            template.process(root, out);
            IOUtils.closeQuietly(out);

            // Selection builder
            outputFile = new File(entityDir, entity.getNameCamelCase() + "Selection.java");
            out = new OutputStreamWriter(new FileOutputStream(outputFile));
            root.put("entity", entity);
            template = getFreeMarkerConfig().getTemplate("selection.ftl");
            template.process(root, out);
            IOUtils.closeQuietly(out);


            // Enums (if any)
            for (Field field : entity.getFields()) {
                if (field.isEnum()) {
                    outputFile = new File(entityDir, field.getEnumName() + ".java");
                    out = new OutputStreamWriter(new FileOutputStream(outputFile));
                    root.put("entity", entity);
                    root.put("field", field);
                    template = getFreeMarkerConfig().getTemplate("enum.ftl");
                    template.process(root, out);
                    IOUtils.closeQuietly(out);
                }
            }
        }
    }

    private void generateContentProvider(Arguments arguments) throws IOException, JSONException, TemplateException {
        Template template = getFreeMarkerConfig().getTemplate("contentprovider.ftl");
        JSONObject config = getConfig(arguments.inputDir);
        String providerJavaPackage = config.getString(Json.PROVIDER_JAVA_PACKAGE);
        File providerDir = new File(arguments.outputDir, providerJavaPackage.replace('.', '/'));
        providerDir.mkdirs();
        File outputFile = new File(providerDir, config.getString(Json.PROVIDER_CLASS_NAME) + ".java");
        Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));

        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", config);
        root.put("model", Model.get());
        root.put("header", Model.get().getHeader());

        template.process(root, out);
    }

    private void generateIntentService(Arguments arguments) throws IOException, JSONException, TemplateException {
        Template template = getFreeMarkerConfig().getTemplate("intentservice.ftl");
        JSONObject config = getConfig(arguments.inputDir);
        String apiJavaPackage = config.getString(Json.PROJECT_PACKAGE_ID) + ".api";
        File apiDir = new File(arguments.outputDir, apiJavaPackage.replace('.', '/'));
        apiDir.mkdirs();
        File outputFile = new File(apiDir, "ApiService.java");
        Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));

        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", config);
        root.put("model", Model.get());
        root.put("header", Model.get().getHeader());

        template.process(root, out);
    }

    private void generateRestService(Arguments arguments) throws IOException, JSONException, TemplateException {
        Template template = getFreeMarkerConfig().getTemplate("retroservice.ftl");
        JSONObject config = getConfig(arguments.inputDir);
        String apiJavaPackage = config.getString(Json.PROJECT_PACKAGE_ID) + ".api";
        File apiDir = new File(arguments.outputDir, apiJavaPackage.replace('.', '/'));
        apiDir.mkdirs();
        File outputFile = new File(apiDir, "RestService.java");
        Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));

        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", config);
        root.put("model", Model.get());
        root.put("header", Model.get().getHeader());

        template.process(root, out);
    }

    private void generateManifestItems(Arguments arguments) throws IOException, JSONException, TemplateException {
        Template template = getFreeMarkerConfig().getTemplate("add_to_manifest.ftl");
        JSONObject config = getConfig(arguments.inputDir);
        File outputFile = new File(arguments.outputDir, "__add_to_manifest.txt");
        Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));

        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", config);
        root.put("model", Model.get());
        root.put("header", Model.get().getHeader());

        template.process(root, out);
    }

    private void generateSqliteHelper(Arguments arguments) throws IOException, JSONException, TemplateException {
        Template template = getFreeMarkerConfig().getTemplate("sqlitehelper.ftl");
        JSONObject config = getConfig(arguments.inputDir);
        String providerJavaPackage = config.getString(Json.PROVIDER_JAVA_PACKAGE);
        File providerDir = new File(arguments.outputDir, providerJavaPackage.replace('.', '/'));
        providerDir.mkdirs();
        File outputFile = new File(providerDir, config.getString(Json.SQLITE_HELPER_CLASS_NAME) + ".java");
        Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));

        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", config);
        root.put("model", Model.get());
        root.put("header", Model.get().getHeader());

        template.process(root, out);
    }

    private void generateModels(Arguments arguments) throws IOException, JSONException, TemplateException {
        JSONObject config = getConfig(arguments.inputDir);
        File baseDir = new File(arguments.outputDir, config.getString(Json.PROJECT_PACKAGE_ID).replace('.', '/'));
        File modelClassesDir = new File(baseDir, "model");
        modelClassesDir.mkdirs();
        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", getConfig(arguments.inputDir));
        root.put("header", Model.get().getHeader());
        for (Entity entity : Model.get().getEntities()) {
            File outputFile = new File(modelClassesDir, entity.getNameCamelCase() + "Model.java");
            Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));
            root.put("entity", entity);
            Template template = getFreeMarkerConfig().getTemplate("model.ftl");
            template.process(root, out);
            IOUtils.closeQuietly(out);
        }
    }

    private void generateFragments(Arguments arguments) throws IOException, JSONException, TemplateException {
        JSONObject config = getConfig(arguments.inputDir);
        File baseDir = new File(arguments.outputDir, config.getString(Json.PROJECT_PACKAGE_ID).replace('.', '/'));
        File fragmentClassDir = new File(baseDir, "fragment");
        fragmentClassDir.mkdirs();
        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", getConfig(arguments.inputDir));
        root.put("header", Model.get().getHeader());
        for (Entity entity : Model.get().getEntities()) {
            File outputFile = new File(fragmentClassDir, entity.getNameCamelCase() + "ListFragment.java");
            Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));
            root.put("entity", entity);
            Template template = getFreeMarkerConfig().getTemplate("fragment.ftl");
            template.process(root, out);
            IOUtils.closeQuietly(out);
        }
    }

    private void generateViews(Arguments arguments) throws IOException, JSONException, TemplateException {
        JSONObject config = getConfig(arguments.inputDir);
        File baseDir = new File(arguments.outputDir, config.getString(Json.PROJECT_PACKAGE_ID).replace('.', '/'));
        File viewDir = new File(baseDir, "ui/viewmodel");
        File resDir = new File(arguments.outputDir+"/res", "layout");
        viewDir.mkdirs();
        resDir.mkdirs();
        Map<String, Object> root = new HashMap<String, Object>();
        root.put("config", getConfig(arguments.inputDir));
        root.put("header", Model.get().getHeader());
        for (Entity entity : Model.get().getEntities()) {
            File outputFile = new File(viewDir, entity.getNameCamelCase() + "View.java");
            Writer out = new OutputStreamWriter(new FileOutputStream(outputFile));
            root.put("entity", entity);
            Template template = getFreeMarkerConfig().getTemplate("view.ftl");
            template.process(root, out);
            IOUtils.closeQuietly(out);

            outputFile = new File(resDir, "view_" + entity.getNameLowerCase() + ".xml");
            out = new OutputStreamWriter(new FileOutputStream(outputFile));
            root.put("entity", entity);
            template = getFreeMarkerConfig().getTemplate("layout.ftl");
            template.process(root, out);
            IOUtils.closeQuietly(out);
        }
    }

    private void go(String[] args) throws IOException, JSONException, TemplateException {
        Arguments arguments = new Arguments();
        JCommander jCommander = new JCommander(arguments, args);
        jCommander.setProgramName("GenerateAndroidProvider");

        if (arguments.help) {
            jCommander.usage();
            return;
        }

        JSONObject config = getConfig(arguments.inputDir);

        loadModel(arguments.inputDir);
        if(config.optBoolean(Json.GENERATE_PROVIDER, true)) {
            generateColumns(arguments);
            generateWrappers(arguments);
            generateContentProvider(arguments);
            generateSqliteHelper(arguments);
        }
        if(config.optBoolean(Json.GENERATE_API, true)) {
            generateIntentService(arguments);
            generateRestService(arguments);
        }
        if(config.optBoolean(Json.GENERATE_API, true) || config.optBoolean(Json.GENERATE_PROVIDER, true)) {
            generateManifestItems(arguments);
        }
        if(config.optBoolean(Json.GENERATE_VIEWS, true)){
            generateViews(arguments);
        }

        if(config.optBoolean(Json.GENERATE_MODELS, true)){
            generateModels(arguments);
        }

        if(config.optBoolean(Json.GENERATE_FRAGMENT)){
            generateFragments(arguments);
        }
    }

    public static void main(String[] args) throws Exception {
        new Main().go(args);
    }
}
