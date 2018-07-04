package org.codinjutsu.tools.mongo.view.model;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import org.apache.commons.lang.StringUtils;
import org.codinjutsu.tools.mongo.ServerConfiguration;
import org.codinjutsu.tools.mongo.model.MongoCollection;
import org.codinjutsu.tools.mongo.model.MongoDatabase;
import org.codinjutsu.tools.mongo.model.MongoServer;
import org.codinjutsu.tools.mongo.utils.GuiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.HashMap;
import java.util.Map;

public class MongoTreeBuilder extends AbstractTreeBuilder {


    private static final Icon MONGO_SERVER = GuiUtils.loadIcon("mongo_logo.png");
    private static final Icon MONGO_DATABASE = GuiUtils.loadIcon("database.png");
    private static final Icon MONGO_COLLECTION = GuiUtils.loadIcon("folder.png");
    private static final Icon MONGO_SERVER_ERROR = GuiUtils.loadIcon("mongo_warning.png");

    private Map<String, MongoServer> serverConfigurations = new HashMap<>();

    private static final RootDescriptor ROOT_DESCRIPTOR = new RootDescriptor();

    public MongoTreeBuilder(@NotNull Tree tree) {
        init(tree, new DefaultTreeModel(new DefaultMutableTreeNode()), new MyTreeStructure(), (o1, o2) -> {
            if (o1 instanceof ServerDescriptor && o2 instanceof ServerDescriptor) {
                return ((MongoServer) o1.getElement()).getLabel().compareTo(((MongoServer) o2.getElement()).getLabel());
            } else if (o1 instanceof DatabaseDescriptor && o2 instanceof DatabaseDescriptor) {
                String cn1 = ((DatabaseDescriptor) o1).getElement().getName();
                String cn2 = ((DatabaseDescriptor) o2).getElement().getName();
                return cn1.compareTo(cn2);
            } else if (o1 instanceof CollectionDescriptor && o2 instanceof CollectionDescriptor) {
                String cn1 = ((CollectionDescriptor) o1).getElement().getName();
                String cn2 = ((CollectionDescriptor) o2).getElement().getName();
                return cn1.compareTo(cn2);
            }
            return 0;
        }, true);
        initRootNode();
    }

    public void addConfiguration(@NotNull ServerConfiguration serverConfiguration) {
        MongoServer mongoServer = new MongoServer(serverConfiguration);
        serverConfigurations.put(serverConfiguration.getLabel(), mongoServer);
        queueUpdateFrom(RootDescriptor.ROOT, true);
    }

    public void removeConfiguration(MongoServer mongoServer) {
        serverConfigurations.remove(mongoServer.getLabel());
        queueUpdateFrom(RootDescriptor.ROOT, true);
    }

    public void removeDatabase(MongoDatabase mongoDatabase) {
        MongoServer parentServer = mongoDatabase.getParentServer();
        parentServer.getDatabases().remove(mongoDatabase);
        queueUpdateFrom(parentServer, true);
    }

    public void removeCollection(MongoCollection mongoCollection) {
        MongoDatabase parentDatabase = mongoCollection.getParentDatabase();
        parentDatabase.getCollections().remove(mongoCollection);
        queueUpdateFrom(parentDatabase, true);
    }

    public void collapseAll() {
        this.collapseChildren(RootDescriptor.ROOT, null);
    }

    private class MyTreeStructure extends AbstractTreeStructure {
        @Override
        public Object getRootElement() {
            return RootDescriptor.ROOT;
        }

        @Override
        public Object[] getChildElements(Object element) {
            if (element == RootDescriptor.ROOT) {
                return ArrayUtil.toObjectArray(serverConfigurations.values());
            } else if (element instanceof MongoServer) {
                return ArrayUtil.toObjectArray(((MongoServer) element).getDatabases());
            } else if (element instanceof MongoDatabase) {
                return ArrayUtil.toObjectArray(((MongoDatabase) element).getCollections());
            }
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }

        @Nullable
        @Override
        public Object getParentElement(Object element) {
            if (element == RootDescriptor.ROOT) {
                return null;
            } else if (element instanceof MongoServer) {
                return RootDescriptor.ROOT;
            } else if (element instanceof MongoDatabase) {
                return ((MongoDatabase) element).getParentServer();
            } else if (element instanceof MongoCollection) {
                return ((MongoCollection) element).getParentDatabase();
            } else {
                return null;
            }
        }

        @NotNull
        @Override
        public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
            if (element == RootDescriptor.ROOT) {
                return ROOT_DESCRIPTOR;
            } else if (element instanceof MongoServer) {
                return new ServerDescriptor(parentDescriptor, (MongoServer) element);
            } else if (element instanceof MongoDatabase) {
                return new DatabaseDescriptor(parentDescriptor, (MongoDatabase) element);
            } else if (element instanceof MongoCollection) {
                return new CollectionDescriptor(parentDescriptor, (MongoCollection) element);
            }

            throw new IllegalStateException("Element not supported : " + element.getClass().getName());
        }


        @Override
        public void commit() {
            // do nothing
        }

        @Override
        public boolean hasSomethingToCommit() {
            return false;
        }
    }

    private static abstract class MyNodeDescriptor<T> extends PresentableNodeDescriptor<T> {
        private final T myObject;

        MyNodeDescriptor(@Nullable NodeDescriptor parentDescriptor, @NotNull T object) {
            super(null, parentDescriptor);
            myObject = object;
        }

        @Override
        public T getElement() {
            return myObject;
        }
    }

    private static class RootDescriptor extends MyNodeDescriptor<Object> {
        public static final Object ROOT = new Object();

        private RootDescriptor() {
            super(null, ROOT);
        }

        @Override
        protected void update(PresentationData presentation) {
            presentation.addText("<root>", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }

    public static class ServerDescriptor extends MyNodeDescriptor<MongoServer> {
        ServerDescriptor(NodeDescriptor parentDescriptor, MongoServer server) {
            super(parentDescriptor, server);
        }

        @Override
        protected void update(PresentationData presentation) {
            MongoServer mongoServer = getElement();
            if (MongoServer.Status.ERROR.equals(mongoServer.getStatus())) {
                presentation.setIcon(MONGO_SERVER_ERROR);
            } else {
                presentation.setIcon(MONGO_SERVER);
            }

            String label = mongoServer.getLabel();
            if (MongoServer.Status.LOADING.equals(mongoServer.getStatus())) {
                label += " (loading)";
            }
            presentation.addText(label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            presentation.setTooltip(StringUtils.join(mongoServer.getServerUrls(), ","));
        }
    }

    public static class DatabaseDescriptor extends MyNodeDescriptor<MongoDatabase> {
        DatabaseDescriptor(NodeDescriptor parentDescriptor, MongoDatabase database) {
            super(parentDescriptor, database);
        }

        @Override
        protected void update(PresentationData presentation) {
            presentation.setIcon(MONGO_DATABASE);
            presentation.addText(getElement().getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }

    public static class CollectionDescriptor extends MyNodeDescriptor<MongoCollection> {
        CollectionDescriptor(NodeDescriptor parentDescriptor, MongoCollection collection) {
            super(parentDescriptor, collection);
        }

        @Override
        protected void update(PresentationData presentation) {
            presentation.setIcon(MONGO_COLLECTION);
            presentation.addText(getElement().getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }
}