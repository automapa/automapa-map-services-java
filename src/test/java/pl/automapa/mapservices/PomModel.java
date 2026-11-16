package pl.automapa.mapservices;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Minimal read-only view of the project pom.xml used by sentinel tests. */
final class PomModel {

    private final Element project;

    private PomModel(Element project) {
        this.project = project;
    }

    static PomModel load(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        return new PomModel(document.getDocumentElement());
    }

    String projectVersion() {
        Element version = child(project, "version");
        return version == null ? null : version.getTextContent().trim();
    }

    /** Returns the trimmed text of a direct child of project, e.g. {@code groupId}, or {@code null}. */
    String projectText(String name) {
        return childText(project, name);
    }

    /** Returns dependencies from project/dependencies (not dependencyManagement). */
    List<Element> dependencies() {
        List<Element> result = new ArrayList<>();
        Element dependencies = child(project, "dependencies");
        if (dependencies == null) {
            return result;
        }
        NodeList nodes = dependencies.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && "dependency".equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    /** Returns a direct child element of project, e.g. {@code scm}, or {@code null}. */
    Element projectElement(String name) {
        return child(project, name);
    }

    /** Returns project/build/plugins (the default build, without profiles), or {@code null}. */
    Element buildPlugins() {
        return child(child(project, "build"), "plugins");
    }

    /** Returns the profile with the given id, or {@code null}. */
    Element profile(String id) {
        for (Element profile : children(child(project, "profiles"), "profile")) {
            if (id.equals(childText(profile, "id"))) {
                return profile;
            }
        }
        return null;
    }

    /** Returns artifact ids of the plugin elements inside a plugins element. */
    static List<String> pluginArtifactIds(Element plugins) {
        List<String> result = new ArrayList<>();
        for (Element plugin : children(plugins, "plugin")) {
            result.add(childText(plugin, "artifactId"));
        }
        return result;
    }

    /** Returns the plugin with the given artifact id inside a plugins element, or {@code null}. */
    static Element plugin(Element plugins, String artifactId) {
        for (Element plugin : children(plugins, "plugin")) {
            if (artifactId.equals(childText(plugin, "artifactId"))) {
                return plugin;
            }
        }
        return null;
    }

    static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    static String childText(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        Element element = child(parent, name);
        return element == null ? null : element.getTextContent().trim();
    }
}
