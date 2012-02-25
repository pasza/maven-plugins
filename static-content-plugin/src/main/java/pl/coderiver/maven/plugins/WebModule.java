package pl.coderiver.maven.plugins;

import com.sun.java.xml.ns.j2Ee.WebType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: pmilewski
 * Date: 1/24/12 12:03 PM
 */
public class WebModule {

    private String uri;

    private String contextRoot;

    public String getUri() {
        return uri;
    }

    public String getContextRoot() {
        return contextRoot;
    }

    public WebModule(String uri, String contextRoot) {
        this.uri = uri;
        this.contextRoot = contextRoot;
    }

    public static List<WebModule> fromWebTypes(List<WebType> webTypes) {
        List<WebModule> webModules = new ArrayList<WebModule>();
        for (WebType webType : webTypes) {
            webModules.add(fromWebType(webType));
        }
        return webModules;
    }

    private static WebModule fromWebType(WebType webType) {
        WebModule webModule = new WebModule(webType.getWebUri().getStringValue(), webType.getContextRoot().getStringValue());
        return webModule;
    }
}
