package com.telenet.scannersservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ResourceBundle;

import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.BasicCommand;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.commandLine.BasicListener;
import org.netbeans.lib.cvsclient.commandLine.CommandFactory;
import org.netbeans.lib.cvsclient.commandLine.GetOpt;
import org.netbeans.lib.cvsclient.commandLine.ListenerProvider;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;
import org.netbeans.lib.cvsclient.connection.ConnectionFactory;
import org.netbeans.lib.cvsclient.connection.PServerConnection;
import org.netbeans.lib.cvsclient.connection.StandardScrambler;
import org.netbeans.lib.cvsclient.event.CVSListener;

/**
 * CVS client utility (command line tool) in Java
 * 
 * @author suraj.waragade
 */
public class LoginCVS {

	private String repository;

	private String localPath;

	private Connection connection;

	private Client client;

	private GlobalOptions globalOptions;

	private int port = 0;
	
	public void setRepository(String repository) {
		this.repository = repository;
	}

	public void setLocalPath(String localPath) {
		this.localPath = localPath;
	}

	public void setGlobalOptions(GlobalOptions globalOptions) {
		this.globalOptions = globalOptions;
	}

	// ==========================================================================
	// ==========================================================================
	// ==========================================================================
	public static void main(String[] args) {
		String[] commands = new String[Constants.TEN];
		commands[0] = Constants.LOGIN;

		// parameters :
		// args = terminal Command which needs to be executed
		// CVS login path
		// port

		if (processCommand(commands, Constants.CVSPATH, System.out, System.err)) {
			System.out.println("Success");
		} else {
			System.out.println("Failed");
		}
	}

	/**
	 * Process the CVS command passed in args[] array
	 */

	public static boolean processCommand(String[] args, String localPath, PrintStream loggerOut,
			PrintStream logError) {

		GlobalOptions globalOptions = new GlobalOptions();
		globalOptions.setCVSRoot(localPath);

		int commandIndex = -1;
		try {
			commandIndex = processGlobalOptions(args, globalOptions, logError);
			if (commandIndex == Constants.NTEN) {
				return true;
			}
		} catch (IllegalArgumentException e) {
			logError.println("Invalid argument: " + e);
			return false;
		}

		if (globalOptions.getCVSRoot() == null) {
			logError.println("No CVS root is set. Use the cvs.root "
					+ "property, e.g. java -Dcvs.root=\":pserver:user@host:/usr/cvs\""
					+ " or start the application in a directory containing a CVS subdirectory"
					+ " or use the -d command switch.");
			return false;
		}

		// parse the CVS root into its constituent parts
		CVSRoot root = null;
		final String cvsRoot = globalOptions.getCVSRoot();
		try {
			root = CVSRoot.parse(cvsRoot);
		} catch (Exception e) {
			logError.println("Incorrect format for CVSRoot: " + cvsRoot + "\nThe correct format is: "
					+ "[:method:][[user]@][hostname:[port]]/path/to/repository" + "\nwhere \"method\" is pserver.");
			logError.println(e);
			return false;
		}

		// if we had some options without any command, then return false
		if (commandIndex >= args.length) {
			showUsage(logError);
			return false;
		}

		final String command = args[commandIndex];
		if (command.equals(Constants.LOGIN)) {
			if (CVSRoot.METHOD_PSERVER.equals(root.getMethod())) {
				return performLogin(root.getUserName(), root.getHostName(), root.getRepository(),						globalOptions);
			} else {
				logError.println("login does not apply for connection type " + "\'" + root.getMethod() + "\'");
				return false;
			}
		}

		// this is not login, but a 'real' cvs command, so need to construct it,
		// set the options, and then connect to the server and execute it

		Command c = null;
		try {
			c = CommandFactory.getDefault().createCommand(command, args, ++commandIndex, globalOptions, localPath);
		} catch (Exception e) {
			logError.println("Illegal argument: " + e);
			return false;
		}
		String password = null;

		if (CVSRoot.METHOD_PSERVER.equals(root.getMethod())) {
			password = root.getPassword();
			if (password != null) {
				password = StandardScrambler.getInstance().scramble(password);
			} else {
				password = StandardScrambler.getInstance().scramble("");
				// passing an empty password for null cases
			}
		}
		LoginCVS cvsCommand = new LoginCVS();
		cvsCommand.setGlobalOptions(globalOptions);
		cvsCommand.setRepository(root.getRepository());
		cvsCommand.setLocalPath(localPath);
		try {
			cvsCommand.connect(root, password);

			CVSListener list;
			if (c instanceof ListenerProvider) {
				list = ((ListenerProvider) c).createCVSListener(loggerOut, logError);
			} else {
				list = new BasicListener(loggerOut, logError);
			}
			cvsCommand.addListener(list);
			return cvsCommand.executeCommand(c);
			
		} catch (AuthenticationException aex) {
			logError.println("Error:" +aex);
			return false;
		} catch (CommandAbortedException caex) {
			logError.println("Error: " + caex);
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception t) {
			logError.println("Error: " + t);
			t.printStackTrace(logError);
			return false;
		} finally {
			if (cvsCommand != null) {
				cvsCommand.close(logError);
			}
		}
	}

	/**
	 * Process global options passed into the application
	 */
	@SuppressWarnings("static-access")
	private static int processGlobalOptions(String[] args, GlobalOptions globalOptions, PrintStream stderr) {
		final String getOptString = globalOptions.getOptString();
		GetOpt go = new GetOpt(args, getOptString);
		int ch = -1;
		boolean usagePrint = false;
		while ((ch = go.getopt()) != go.optEOF) {
			boolean success = globalOptions.setCVSCommand((char) ch, go.optArgGet());
			if (!success) {
				usagePrint = true;}
		}
		if (usagePrint) {
			showUsage(stderr);
			return Constants.NTEN;
		}
		return go.optIndexGet();
	}

	/**
	 * Supplementary method to
	 */
	private static void showUsage(PrintStream stderr) {
		String usageStr = ResourceBundle.getBundle(LoginCVS.class.getPackage().getName() + ".Bundle")
				.getString("MSG_HelpUsage");
		stderr.println(usageStr);
	}

	/**
	 * Perform the 'login' command, asking the user for a password.
	 */
	private static boolean performLogin(String userName, String hostName, String repository, GlobalOptions globalOptions) {
		PServerConnection c = new PServerConnection();
		c.setUserName(userName);
		String password = null;
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			System.out.print("Enter password: ");
			password = in.readLine();
		} catch (IOException e) {
			System.err.println("Could not read password: " + e);
			return false;
		}

		String encodedPassword = StandardScrambler.getInstance().scramble(password);
		c.setEncodedPassword(encodedPassword);
		c.setHostName(hostName);
		c.setRepository(repository);
		try {
			c.verify();
		} catch (AuthenticationException e) {
			System.err.println("Could not login to host " + hostName);
			System.err.println("Error ::"+e);
			return false;
		}

		System.out.println("Logged in successfully to repository " + repository + " on host " + hostName);
		return true;
	}

	// ============== Client methods
	/**
	 * Execute a configured CVS command
	 */
	public boolean executeCommand(Command command) throws CommandException, AuthenticationException {
		return client.executeCommand(command, globalOptions);
	}

	/**
	 * Creates the connection and the client and connects.
	 */
	private void connect(CVSRoot root, String password) throws AuthenticationException, CommandAbortedException {
		connection = ConnectionFactory.getConnection(root);
		if (CVSRoot.METHOD_PSERVER.equals(root.getMethod())) {
			((PServerConnection) connection).setEncodedPassword(password);
		}
		connection.open();

		client = new Client(connection, new StandardAdminHandler());
		client.setLocalPath(localPath);
	}

	private void addListener(CVSListener listener) {
		if (client != null) {
			// adding a listener to the client
			client.getEventManager().addCVSListener(listener);
		}
	}

	private void close(PrintStream stderr) {
		try {
			connection.close();
		} catch (IOException e) {
			stderr.println("Unable to close connection: " + e);
		}
	}

}