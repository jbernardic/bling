package main.java.bernardic.jb.server;

import java.util.Properties;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;

public class Server {
	private static Database database;
	public static Database getDatabase() { return database; }
	public static void main(String[] args) {
		Configs.init();
		Properties dbProps = Configs.get("database");
		database = new Database(dbProps.getProperty("url"), dbProps.getProperty("user"), dbProps.getProperty("password"));
		database.testConnection();
		database.createUsers();
		Configuration config = new Configuration();
		config.setHostname("localhost");
		config.setPort(5000);
		SocketIOServer server = new SocketIOServer(config);
		Auth auth = new Auth(server);
		server.addConnectListener(new ConnectListener() {
			@Override
			public void onConnect(SocketIOClient client) {
				System.out.println("Connnected to " + client);
			}
		});
		auth.init();
        server.start();
	}
}
