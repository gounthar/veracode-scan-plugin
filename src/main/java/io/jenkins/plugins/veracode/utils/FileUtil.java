/*******************************************************************************
 * Copyright (c) 2014 Veracode, Inc. All rights observed.
 *
 * Available for use by Veracode customers as described in the accompanying license agreement.
 *
 * Send bug reports or enhancement requests to support@veracode.com.
 *
 * See the license agreement for conditions on submitted materials.
 ******************************************************************************/

package io.jenkins.plugins.veracode.utils;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Properties;

import org.jenkinsci.remoting.RoleChecker;

import io.jenkins.plugins.veracode.VeracodeNotifier;
import io.jenkins.plugins.veracode.VeracodeNotifier.VeracodeDescriptor;
import io.jenkins.plugins.veracode.common.Constant;
import com.veracode.apiwrapper.cli.VeracodeCommand;

/**
 * A utility class for working with files and directories.
 *
 *
 */
public final class FileUtil {

	private static final String VERACODE_PROPERTIES_FILE_NAME = "veracode.properties";

	/**
	 * Deletes the file represented by the specified {@link java.io.File File}
	 * object. If {@code file} represents a directory it also recursively
	 * deletes its contents.
	 *
	 * @param file File
	 * @return boolean
	 */
	public static boolean deleteDirectory(File file) {
		if (file.isDirectory()) {
			File[] list = file.listFiles();
			if (list != null) {
				for (File f : list) {
					deleteDirectory(f);
				}
			}
		}
		return file.delete();
	}

	/**
	 * Returns a String array whose elements correspond to the textual
	 * representation of the file paths of the files represented by the elements
	 * of the specified {@link hudson.FilePath FilePath} array.
	 *
	 * @param filePaths FilePath[]
	 * @return String[]
	 * @throws IOException exception
	 * @throws InterruptedException exception
	 */
	public static String[] getStringFilePaths(FilePath[] filePaths) throws IOException, InterruptedException {
		String[] stringFilePaths = new String[filePaths.length];
		for (int x = 0; x < filePaths.length; x++) {
			try {
				stringFilePaths[x] = getStringFilePath(filePaths[x]);
			} catch (IOException ioe) {
				throw new IOException(String.format("Could not locate the specified file: %s.", filePaths[x]), ioe);
			} catch (InterruptedException ie) {
				throw new InterruptedException(String.format("Could not locate the specified file: %s.", filePaths[x]));
			}
		}
		return stringFilePaths;
	}

	/**
	 * Returns a String that corresponds to the textual representation of the
	 * file path of the file represented by the specified
	 * {@link hudson.FilePath FilePath} object.
	 *
	 * @param filePath FilePath
	 * @return String
	 * @throws IOException excepton
	 * @throws InterruptedException exception
	 */
	public static String getStringFilePath(FilePath filePath) throws IOException, InterruptedException {
		// because the FileCallable interface extends Serializable the
		// argument to the "act" method should not be an instance of a class
		// that contains an implicit reference to an instance of a
		// non-serializable class (don't use an anonymous inner class).
		return filePath.act(new FileCallableImpl());
	}

	/**
	 * Implements {@link hudson.FilePath.FileCallable FileCallable}'s
	 * {@link hudson.FilePath.FileCallable#invoke(File, VirtualChannel) invoke}
	 * method, which is executed on the machine containing the file whose file
	 * path is represented by the {@link hudson.FilePath FilePath} object on
	 * which the {@link hudson.FilePath#act(FilePath.FileCallable) act} method
	 * is called.
	 *
	 *
	 */
	public static final class FileCallableImpl implements FilePath.FileCallable<String> {
		private static final long serialVersionUID = 1L;

		public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
			return f.getPath();
		}

		@Override
		public void checkRoles(RoleChecker arg0) throws SecurityException {
			// TODO Auto-generated method stub
		}
	}

	// copy the wrapper to the remote location
    public static boolean copyJarFiles(AbstractBuild<?, ?> build, FilePath local, FilePath remote, PrintStream ps)
            throws Exception {
        boolean bRet = false;
        try {
            Node node = build.getBuiltOn();
            if (node == null) {
            	throw new RuntimeException("Cannot locate the build node.");
            }
            local.copyRecursiveTo(Constant.inclusive, null, remote);

            // now make a copy of the jar as 'VeracodeJavaAPI.jar' as the name
            // of the jarfile in the plugin
            // will change depending on the wrapper version it has been built
            // with

            FilePath[] files = remote.list(Constant.inclusive);
            String jarName = files[0].getRemote();
            FilePath oldJar = new FilePath(node.getChannel(), jarName);
            String newJarName = jarName.replaceAll(Constant.regex, Constant.execJarFile + "$2");
            FilePath newjarFilePath = new FilePath(node.getChannel(), newJarName);
            oldJar.copyToWithPermission(newjarFilePath);
            bRet = true;
        } catch (RuntimeException ex) {
        	VeracodeDescriptor veracodeDescriptor = (VeracodeDescriptor) Jenkins.getInstance().getDescriptor(VeracodeNotifier.class);
            if (veracodeDescriptor != null && veracodeDescriptor.getFailbuild()) {
                ps.println("Failed to copy the jarfiles\n");
            }
        }
        return bRet;
    }

    /**
	 * Returns the java wrapper location situated in master
	 * @return FilePath
     * @throws URISyntaxException exception
	 */
	public static FilePath getLocalWorkspaceFilepath() throws URISyntaxException {
		File wrapperFile = new File(
				VeracodeCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
		return new FilePath(wrapperFile.getParentFile());
	}

	/**
	 * Remove the properties for the specified
	 *
	 * @param run Run
	 * @return boolean
	 * @param listener TaskListener
	 */
    public static boolean cleanUpBuildProperties(Run<?, ?> run, TaskListener listener) {
    	File file = null;
    	try {
    		// run.getRootDir().getParent() is the builds directory
	    	file = new File(run.getRootDir().getParent() + File.separator + VERACODE_PROPERTIES_FILE_NAME);
	    	if (file.exists()) {
	    		Files.delete(file.toPath());
			}
	    	return true;
    	} catch(IOException e) {
    		listener.getLogger().println(Constant.NEWLINE + Constant.NEWLINE + e.getMessage());
    		return false;
    	}
    }

	/**
	 * Creates the properties for the specified
	 *
	 * @param run Run
	 * @param properties Properties
	 * @param listener TaskListener
	 * @return boolean
	 * @throws IOException exception
	 */
	public static boolean createBuildPropertiesFile(Run<?, ?> run, Properties properties, TaskListener listener) throws IOException {
		File file = null;
		FileOutputStream fileOutputStream = null;
		try {
	    	file = new File(run.getRootDir().getParent() + File.separator + VERACODE_PROPERTIES_FILE_NAME);
			fileOutputStream = new FileOutputStream(file);
			properties.store(fileOutputStream, "Veracode");
			return true;
		} catch (FileNotFoundException e) {
			listener.getLogger().println(Constant.NEWLINE + Constant.NEWLINE + e.getMessage());
			return false;
		} catch (IOException e) {
			listener.getLogger().println(Constant.NEWLINE + Constant.NEWLINE + e.getMessage());
			return false;
		} finally {
			if (fileOutputStream != null) {
				fileOutputStream.close();
			}
		}
	}

	/**
	 * Returns the properties for the specified
	 *
	 * @param run Run
	 * @param listener TaskListener
	 * @return Properties
	 * @throws IOException exception
	 */
	public static Properties readBuildPropertiesFile(Run<?, ?> run, TaskListener listener) throws IOException {
		File file = null;
		FileInputStream fileInputStream = null;
		Properties properties = null;
		try {
	    	file = new File(run.getRootDir().getParent() + File.separator + VERACODE_PROPERTIES_FILE_NAME);
			if (file.exists()) {
				fileInputStream = new FileInputStream(file);
				properties = new Properties();
				properties.load(fileInputStream);
			}
		} catch (FileNotFoundException e) {
			listener.getLogger().print(Constant.NEWLINE + Constant.NEWLINE + e.getMessage());
		} catch (IOException e) {
			listener.getLogger().print(Constant.NEWLINE + Constant.NEWLINE + e.getMessage());
		} finally {
			if (fileInputStream != null) {
				fileInputStream.close();
			}
			cleanUpBuildProperties(run, listener);
		}
		return properties;
	}

	private FileUtil(){}
}