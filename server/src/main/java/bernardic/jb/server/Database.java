package main.java.bernardic.jb.server;

import java.sql.*;
import java.util.UUID;
import org.mindrot.jbcrypt.BCrypt;

import main.java.bernardic.jb.server.models.Group;
import main.java.bernardic.jb.server.models.User;

public class Database {
	private final String db_url, db_user, db_pass;
	private final Connection getConnection() throws SQLException {return DriverManager.getConnection(db_url, db_user, db_pass);}
	public Database(String url, String user, String pass) {
		this.db_url = url;
		this.db_user = user;
		this.db_pass = pass;
	}
	public void init() {
		testConnection();
		createFunctions();
		createTables();
	}
	public void testConnection() {
	    try(Connection conn = getConnection()){
	    	if(conn == null) {
	    		System.out.println("Failed to connect to PostgreSQL Server");
	    		return;
	    	}
	    	System.out.println("Connected to PostgreSQL Server successfully!");
	    }catch(SQLException e) {
	    	e.printStackTrace();
	    }
	}
	public void createFunctions() {
		try(Connection conn = getConnection()){
			String inviteCodeFunction = "CREATE OR REPLACE FUNCTION update_invite_code(group_uuid UUID) RETURNS text AS $$" + 
					"DECLARE" + 
					"    code text;" + 
					"    done bool;" + 
					"BEGIN" + 
					"    done := false;" + 
					"    WHILE NOT done LOOP" + 
					"        code := substring(md5(''||now()::text||random()::text) for 7);" + 
					"        done := NOT exists(SELECT 1 FROM groups WHERE invite_code=code);" + 
					"    END LOOP;" + 
					"    UPDATE groups SET invite_code = code WHERE groups.uuid = group_uuid;" + 
					"    RETURN code;" + 
					"END;" + 
					"$$ LANGUAGE PLPGSQL VOLATILE;";
	    	conn.createStatement().execute(inviteCodeFunction);
		}catch(SQLException e) {
			e.printStackTrace();
		}
	}
	public void createTables() {
		//Create users table if doesn't exist
		try(Connection conn = getConnection()){
			String users = "CREATE TABLE IF NOT EXISTS users ("
					+ "token UUID PRIMARY KEY, "
					+ "username VARCHAR(15) NOT NULL UNIQUE, "
					+ "email VARCHAR(254) NOT NULL UNIQUE, "
					+ "password VARCHAR(60) NOT NULL, "
					+ "groups UUID[]"
					+ ");";
			String groups = "CREATE TABLE IF NOT EXISTS groups ("
					+ "uuid UUID PRIMARY KEY, "
					+ "name VARCHAR(30) NOT NULL, "
					+ "members UUID[], "
					+ "invite_code CHAR(7)"
					+ ");";
			String messages = "CREATE TABLE IF NOT EXISTS messages ("
					+ "id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
					+ "seens INT DEFAULT 0, "
					+ "group_uuid UUID, "
					+ "message VARCHAR"
					+ ");";
			conn.createStatement().executeUpdate(users);
			conn.createStatement().executeUpdate(groups);
			conn.createStatement().executeUpdate(messages);
		}catch(SQLException e) {
			e.printStackTrace();
		}
	}
	public String addUser(String username, String password, String email) {
		UUID token = UUID.randomUUID();
		try(Connection conn = getConnection()){
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (token, username, email, password) VALUES ('"+token+"', ?,?,?) ON CONFLICT DO NOTHING;");
			stmt.setString(1, username);
			stmt.setString(2, email);
			stmt.setString(3, BCrypt.hashpw(password, BCrypt.gensalt()));
			stmt.executeUpdate();
		}catch(SQLException e) {
			e.printStackTrace();
		}
		return token.toString();
	}
	public User getUser(UUID token) {
		try(Connection conn = getConnection()){
			Statement stmt = conn.createStatement();
			String sql = "SELECT * FROM users WHERE token='" + token + "';";
			ResultSet res = stmt.executeQuery(sql);
			if(res.next()) {
				UUID[] groups = null;
 				if(res.getArray(5) != null) groups =(UUID[])res.getArray(5).getArray();
				return new User(token, res.getString(2), res.getString(3), res.getString(4), groups);	
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	public String authUser(String username, String password) {
		try(Connection conn = getConnection()){
			PreparedStatement stmt = conn.prepareStatement("SELECT password, token FROM users WHERE username=? OR email=?");
			stmt.setString(1, username);
			stmt.setString(2, password);
			ResultSet res = stmt.executeQuery();
			if(res.next() && BCrypt.checkpw(password, res.getString(1))) {
				return res.getString(2);
			}
		}catch(SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	public boolean hasEmail(String email) {
		try(Connection conn = getConnection()){
			PreparedStatement stmt = conn.prepareStatement("SELECT EXISTS(SELECT 1 FROM users WHERE email=?)");
			stmt.setString(1, email);
			ResultSet res = stmt.executeQuery();
			if(res.next()) {
				return res.getBoolean(1);
			}
		}catch(SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	public boolean hasUsername(String username) {
		try(Connection conn = getConnection()){
			PreparedStatement stmt = conn.prepareStatement("SELECT EXISTS(SELECT 1 FROM users WHERE username=?)");
			stmt.setString(1, username);
			ResultSet res = stmt.executeQuery();
			if(res.next()) {
				return res.getBoolean(1);
			}
		}catch(SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	public boolean validateToken(String token) {
		try(Connection conn = getConnection()){
			Statement stmt = conn.createStatement();
			String sql = "SELECT EXISTS(SELECT 1 FROM users WHERE token='"+token+"')";
			ResultSet res = stmt.executeQuery(sql);
			if(res.next()) {
				return res.getBoolean(1);
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	public Group createGroup(String groupName) {
		Group group = new Group(UUID.randomUUID(), groupName, new UUID[] {});
		try(Connection conn = getConnection()){
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO groups (uuid, name) VALUES ('" + group.getGroupUUID() + "',?) ON CONFLICT DO NOTHING;");
			stmt.setString(1, groupName);
			stmt.executeUpdate();
			conn.createStatement().execute("SELECT update_invite_code('"+ group.getGroupUUID() +"');");
		}catch(Exception e) {
			e.printStackTrace();
		}
		return group;
	}
	public void addUserToGroup(User user, Group group) {
		try(Connection conn = getConnection()){
			PreparedStatement stmt = conn.prepareStatement("UPDATE groups SET members = array_append(?, ?) WHERE groups.uuid = '"+ group.getGroupUUID() +"';");
			stmt.setArray(1, conn.createArrayOf("UUID", group.getMembers()));
			stmt.setObject(2, user.getToken());
			stmt.executeUpdate();
			PreparedStatement stmt2 = conn.prepareStatement("UPDATE users SET groups = array_append(?, ?) WHERE users.token = '" + user.getToken() + "';");
			stmt2.setArray(1, conn.createArrayOf("UUID", user.getGroups()));
			stmt2.setObject(2, group.getGroupUUID());
			stmt2.executeUpdate();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	public Group getGroup(UUID groupId) {
		try(Connection conn = getConnection()){
			Statement stmt = conn.createStatement();
			String sql = "SELECT * FROM groups WHERE uuid='" + groupId + "';";
			ResultSet res = stmt.executeQuery(sql);
			if(res.next()) {
				Group group = new Group((UUID)res.getObject(1), res.getString(2), (UUID[])res.getArray(3).getArray());
				return group;	
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	public void addMessage(String groupUUID, String message) {
		try(Connection conn = getConnection()){
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO messages (group_uuid, message) VALUES ('"+ groupUUID +"',?) ON CONFLICT DO NOTHING;");
			stmt.setString(1, message);
			stmt.executeUpdate();
		}catch(SQLException e) {
			e.printStackTrace();
		}
	}
	
}
