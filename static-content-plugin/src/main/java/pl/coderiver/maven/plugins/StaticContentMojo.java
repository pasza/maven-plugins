/**
 * 
 */
package pl.coderiver.maven.plugins;

import java.io.File;
import java.io.IOException;
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import com.sun.java.xml.ns.j2Ee.*;
import com.sun.java.xml.ns.j2Ee.impl.WebTypeImpl;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.AttachedArtifact;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.tar.TarLongFileMode;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.util.FileUtils;

/**
 * Builds static content from  J2EE Enteprise Archive (EAR) Web Modules, WAR and Grails modules.
 *
 * @goal static
 * @execute phase="package"
 *
 * Created by IntelliJ IDEA.
 * User: pmilewski
 * Date: 1/24/12 12:03 PM
 */
public class StaticContentMojo extends AbstractMojo {

	private static final String[] DEFAULT_INCLUDES = new String[]{
		"gif",
		"jpg",
		"swf",
		"png",
		"css",
		"js",
		"ico",
		"wbmp",
        "pdf"
	};


	/**
	 * The maven project.
	 *
	 * @parameter expression="${project}"
	 * @required
	 */
	protected MavenProject project;

	/**
	 * Directory that resources are copied to during the build.
	 *
	 * @parameter expression="${project.build.directory}
	 * @required
	 */
	protected File workDirectory;

	/**
	 * @parameter expression="${project.build.directory}/static_content
	 * @required
	 */
	protected File outputDirectory;

	/**
	 * The ear modules configuration.
	 *
	 * @parameter
	 */
	private Set<String> includesExtensions;
	
	/**
	 * Artifact names that should be excludes from static content.
	 * 
	 * @parameter
	 */
	private Set<String> excludesArtifacts;
	
	/**
	 * 
	 * @parameter
	 */
	private boolean excludeDefaultExtensions;

	/**
	 * @component 
	 */
	private ArchiverManager archiverManager;

	/**
	 * The archive manager.
	 *
	 * @component role="org.codehaus.plexus.archiver.UnArchiver" roleHint="zip"
	 */
	private ZipUnArchiver unArchiver;


	/**
	 * If extensions should be treated case-sensitive
	 * @parameter
	 */
	private boolean caseSensitive;

    private boolean singleWebModule = false;

	/**
	 */
	public static final String STATIC_CONTENT_DIRECTORY = "static_content";

	public static final String APPLICATION_XML_URI = "application.xml";


	/**
	 * @throws MojoExecutionException
	 */
	private void init() throws MojoExecutionException {
		if (excludesArtifacts == null) {
			excludesArtifacts = new HashSet<String>();
		}

        if ("war".equals(project.getPackaging()) || "grails-app".equals(project.getPackaging())) {
            singleWebModule = true;
        }

		File basedir = new File(workDirectory, STATIC_CONTENT_DIRECTORY);
		if (!basedir.exists()) {
			boolean mkdir = basedir.mkdir();
			if (!mkdir) {
				throw new MojoExecutionException("Cannot create " + STATIC_CONTENT_DIRECTORY + " directory");
			}
		} 
		outputDirectory = basedir;
	}
	/** 
	 * (non-Javadoc)
	 * @see org.apache.maven.plugin.Mojo#execute()
	 * 
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		init();
		extractWebModules();
		createArchive();
	}
	
	@SuppressWarnings("unchecked")
	private void extractWebModules() throws MojoExecutionException {
        Set<Artifact> artifacts = new HashSet<Artifact>();
        if (singleWebModule) {
            artifacts.add(project.getArtifact());
        } else {
           artifacts = project.getArtifacts();
        }

		List<WebModule> webTypes = resolveContextRoots();
		for (Artifact artifact : artifacts) {
            if (!excludesArtifacts.contains(artifact.getArtifactId())) {
                String type = artifact.getType();
                if ("grails-app".equals(type)) {
                    type = "war";
                }
                String name = artifact.getArtifactId() + "-" + artifact.getVersion() + "." + type;

                getLog().info("Processing " + name);
                for (WebModule webType : webTypes) {
                    String webUri = webType.getUri();
                    if (webUri == null || name.equals(webUri)) {
                        extractStaticContent(artifact, webType.getContextRoot());
                        break;
                    }
                }
            } else {
                getLog().info("Ignoring excluded artifact " + artifact.getArtifactId());
            }
		}
	}

	@SuppressWarnings("unchecked")
	private void createArchive() throws MojoExecutionException {
		Artifact artifact = new AttachedArtifact(project.getArtifact(), "tar", "static", new DefaultArtifactHandler("tar"));

		String sDestination = artifact.getArtifactId() + "-" + artifact.getVersion() + "-" + artifact.getClassifier() + ".tar";
		try {
			TarArchiver tarArchiver = (TarArchiver) archiverManager.getArchiver( "tar" );

            getLog().debug("Ouput dir: " + outputDirectory);

			File destinationFile = new File(workDirectory, sDestination);
			tarArchiver.setDestFile(destinationFile);
			tarArchiver.setIncludeEmptyDirs(false);

			List<String> dotFiles = FileUtils.getFileNames(outputDirectory, getIncludes(), null, true, caseSensitive);
			int indexOf = outputDirectory.getAbsolutePath().length();
			for (String fullPath : dotFiles) {
				File f = new File(fullPath);
				String path = fullPath.substring(indexOf, fullPath.length());
				tarArchiver.addFile(f, path);
			}

			TarLongFileMode fileMode = new TarLongFileMode();
			fileMode.setValue(TarLongFileMode.GNU);
			tarArchiver.setLongfile(fileMode);
			tarArchiver.createArchive();

			artifact.setFile(destinationFile);
			project.addAttachedArtifact(artifact);
		} catch (ArchiverException e) {
			getLog().error(e);
			throw new MojoExecutionException(e.getMessage());
		} catch (IOException e) {
			getLog().error(e);
			throw new MojoExecutionException(e.getMessage());
		} catch (NoSuchArchiverException e) {
			getLog().error(e);
			throw new MojoExecutionException(e.getMessage());
		}

	}

	private void extractStaticContent(Artifact artifact, String contextRoot) throws MojoExecutionException {
		getLog().info("Extracting static context for artifact " + artifact.getArtifactId() + " to " + contextRoot);
		StringTokenizer tokenizer = new StringTokenizer(contextRoot, "/", false);

		File f = outputDirectory;
		while (tokenizer.hasMoreTokens()) {
			String dir = tokenizer.nextToken();
			f = new File(f, dir);
			f.mkdir();
		}
        File source = artifact.getFile();
        if (source == null) {
            String sourceDest = workDirectory.getPath() + "/" + artifact.getArtifactId() + "-" + artifact.getVersion() + ".war";
            source = new File(sourceDest);
        }

		try {
			unArchiver.setDestDirectory(f);
			unArchiver.setSourceFile(source);
			unArchiver.extract();
		} catch (ArchiverException e) {
			getLog().error(e);
			throw new MojoExecutionException(e.getMessage());
		}

	}

	private List<WebModule> resolveContextRoots() throws MojoExecutionException {
		// Check if deployment descriptor is there
        String packaging = project.getPackaging();
        if (singleWebModule) {
            project.getArtifactId();
            WebModule webModule = new WebModule(null, project.getArtifactId());
            return Arrays.asList(webModule);
        } else {

            File ddFile = new File(workDirectory, APPLICATION_XML_URI);
            if (!ddFile.exists()) {
                throw new MojoExecutionException("Deployment descriptor: "
                        + ddFile.getAbsolutePath() + " does not exist.");
            } else {
                List<WebType> webTypes = resolveEarContextRoots(ddFile);
                return WebModule.fromWebTypes(webTypes);
            }
        }
	}

    private List<WebType> resolveEarContextRoots(File ddFile) throws MojoExecutionException {
        List<WebType> webTypes = new ArrayList<WebType>();
		try {
			ApplicationDocument applicationDocument = ApplicationDocument.Factory.parse(ddFile);
			ApplicationType application = applicationDocument.getApplication();
			ModuleType[] moduleArray = application.getModuleArray();
			for (ModuleType moduleType : moduleArray) {
				if (moduleType.isSetWeb()) {
					WebType webType = moduleType.getWeb();
					String webUri = webType.getWebUri().getStringValue();
					String contextRoot = webType.getContextRoot().getStringValue();
					getLog().info("Found WEB APP: "  + webUri + " with contextRoot " + contextRoot);
					webTypes.add(webType);
				}
			}
		} catch (Exception e) {
			getLog().error("Cannot parser " + APPLICATION_XML_URI, e);
			throw new MojoExecutionException("Cannot parse "
					+ APPLICATION_XML_URI + ": " + e.getMessage());
		}
		return webTypes;
    }


	private String getIncludes() throws MojoExecutionException {
		Set<String> extentions = new HashSet<String>();
		if (includesExtensions != null) {
			extentions.addAll(includesExtensions);
		}
		if (!excludeDefaultExtensions) {
			extentions.addAll(Arrays.asList(DEFAULT_INCLUDES));
		}
		if (extentions.isEmpty()) {
			throw new MojoExecutionException("No file extension given");
		}
		StringBuffer sb = new StringBuffer();
		for (String extension : extentions) {
			sb.append("**/*.").append(extension).append(",");
		}
		sb.deleteCharAt(sb.length()-1);
		getLog().info("Static content will include pattern: " + sb);
		return sb.toString();
	}

}
