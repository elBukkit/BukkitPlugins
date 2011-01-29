package com.elmakers.mine.bukkit.plugins.persistence.stores;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.elmakers.mine.bukkit.plugins.persistence.PersistedClass;
import com.elmakers.mine.bukkit.plugins.persistence.PersistedField;

public abstract class SqlStore extends PersistenceStore
{
	public abstract String getDriverClassName();
	public abstract String getDriverFileName();
	public abstract String getMasterTableName();
	public abstract String getConnectionString(String schema, String user, String password);
	public abstract String getTypeName(SqlType dataType);
	public abstract String getFieldValue(Object field, SqlType dataType);
	
	public boolean onConnect()
	{
		return true;
	}
	
	@Override
	public boolean connect(String schema)
	{
		this.schema = schema;
		
		try 
		{
			// Check to see if the driver is loaded
			String jdbcClass = getDriverClassName();
			try
			{
				Class.forName(jdbcClass);
			}
			catch (ClassNotFoundException e)
			{
				log.info("Persistence: Loading sqlite drivers from plugins folder");
				String fileName = getDriverFileName();
				
				File dataPath = dataFolder.getAbsoluteFile();
				File pluginsPath = new File(dataPath.getParent());
				File cbPath = new File(pluginsPath.getParent());
				File sqlLiteFile = new File(cbPath, fileName + ".jar");
	            if (!sqlLiteFile.exists()) 
	            {
	                log.severe("Persistence: Failed to find sql driver: " + fileName + ".jar");
	                return false;
	            }
	            
	            try 
	            {
	            	URL u = new URL("jar:file:" + sqlLiteFile.getAbsolutePath() + "!/");
	        		URLClassLoader ucl = new URLClassLoader(new URL[] { u });
	        		Driver d = (Driver)Class.forName(jdbcClass, true, ucl).newInstance();
	        		DriverManager.registerDriver(new DriverShim(d));
	            } 
	            catch (MalformedURLException ex) 
	            {
	                log.severe("Persistence: Exception while loading sql drivers");
	                ex.printStackTrace();
	                return false;
	            }
	            catch (IllegalAccessException ex) 
	            {
	                log.severe("Persistence: Exception while loading sql drivers");
	                ex.printStackTrace();
	                return false;
	            }
	            catch (InstantiationException ex) 
	            {
	                log.severe("Persistence: Exception while loading sql drivers");
	                ex.printStackTrace();
	                return false;
	            }
				catch (ClassNotFoundException e1)
				{
					log.severe("Persistence: JDBC class not found in sql jar");
				}
			}			
			// Create or connect to the database
			
			// TODO: user, password
			String user = "";
			String password = "";
					
			connection = DriverManager.getConnection(getConnectionString(schema, user, password));
		}
		catch(SQLException e)
		{
			connection = null;
			log.severe("Permissions: error connecting to sqllite db: " + e.getMessage());
		}
		
		return isConnected() && onConnect();
	}

	@Override
	public void disconnect()
	{
		if (connection != null)
		{
			try
			{
				connection.close();
			}
			catch (SQLException e)
			{
				
			}
		}
		connection = null;
	}

	@Override
	public void validateTable(PersistedClass persisted)
	{
		String tableName = persisted.getTableName();
		String checkQuery = "SELECT name FROM " + getMasterTableName() + " WHERE type='table' AND name='" + tableName + "'";
		boolean tableExists = false;
		try
		{
			PreparedStatement ps = connection.prepareStatement(checkQuery);
			ResultSet rs = ps.executeQuery();
			tableExists = rs.next();
			rs.close();
		}
		catch (SQLException ex)
		{
			ex.printStackTrace();
		}
		if (!tableExists)
		{
			List<PersistedField> fields = persisted.getPersistedFields();
			String createStatement = "CREATE TABLE " + tableName + "(";
			int fieldCount = 0;
			for (PersistedField field : fields)
			{
				SqlType fieldType = getSqlType(field);
				if (fieldCount != 0)
				{
					createStatement += ",";
				}
				fieldCount++;
				createStatement += field.getName() + " " + getTypeName(fieldType);
				if (field.isIdField())
				{
					createStatement += " PRIMARY KEY";
				}
			}
			createStatement += ");";
			
			if (fieldCount == 0)
			{
				log.warning("Persistence: class " + tableName + " has no fields");
				return;
			}
			
			log.info(createStatement);
			log.info("Persistence: Create table " + schema + "." + tableName);
			try
			{
				PreparedStatement ps = connection.prepareStatement(createStatement);
				ps.execute();
			}
			catch (SQLException ex)
			{
				ex.printStackTrace();
			}
		}
		else
		{
			// TODO: validate schema, migrate data if necessary
		}
	}

	@Override
	public boolean loadAll(PersistedClass persisted)
	{
		String tableName = persisted.getTableName();
		String selectQuery = "SELECT ";
		int fieldCount = 0;
		List<PersistedField> fields = persisted.getPersistedFields();
		for (PersistedField field : fields)
		{
			if (fieldCount != 0)
			{
				selectQuery += ", ";
			}
			fieldCount++;
			selectQuery += field.getName();
		}
		if (fieldCount == 0)
		{
			log.warning("Persistence: class " + tableName + " has no fields");
			return false;
		}
		selectQuery +=  " FROM " + tableName + ";";
		
		try
		{
			PreparedStatement ps = connection.prepareStatement(selectQuery);
			ResultSet rs = ps.executeQuery();
			while (rs.next())
			{
				Object newObject = createInstance(rs, persisted);
				if (newObject != null)
				{
					persisted.put(newObject);
				}
			}
			rs.close();
		}
		catch (SQLException ex)
		{
			ex.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	protected Object createInstance(ResultSet rs, PersistedClass persisted)
	{
		Object newObject = null;
		
		try
		{
			newObject = persisted.getPersistClass().newInstance();
			List<PersistedField> fields = persisted.getPersistedFields();
	        for (PersistedField field : fields)
	        {
	        	field.set(newObject, rs.getObject(field.getName()));
	        }
		}
		catch (IllegalAccessException e)
		{
			newObject = null;
			e.printStackTrace();
		}
		catch (InstantiationException e)
		{
			newObject = null;
			e.printStackTrace();
		}
		catch (SQLException e)
		{
			newObject = null;
			log.warning("Persistence error getting fields for " + persisted.getTableName() + ": " + e.getMessage());
		}
		return newObject;
	}

	@Override
	public boolean saveAll(PersistedClass persisted)
	{
		// TODO Auto-generated method stub
		return false;
	}
	
	public boolean isConnected()
	{
		boolean isClosed = true;
		try
		{
			isClosed = connection == null || connection.isClosed();
		}
		catch (SQLException e)
		{
			isClosed = true;
		}
		return (connection != null && !isClosed);
	}
	
	protected SqlType getSqlType(PersistedField field)
	{
		SqlType sqlType = SqlType.NULL;
		
		Class<?> fieldType = field.getType();
		if (fieldType.isAssignableFrom(Integer.class))
		{
			sqlType = SqlType.INTEGER;
		}
		else if (fieldType.isAssignableFrom(Double.class))
		{
			sqlType = SqlType.DOUBLE;
		}
		else if (fieldType.isAssignableFrom(Float.class))
		{
			sqlType = SqlType.DOUBLE;
		}
		else if (fieldType.isAssignableFrom(String.class))
		{
			sqlType = SqlType.STRING;
		}
		else
		{
			log.warning("Persistence: field: " + field.getType().getName() + " not a supported type. Object refences not supported, yet.");
			sqlType = SqlType.NULL;
		}
		
		return sqlType;
	}

	public void setDataFolder(File dataFolder)
	{
		this.dataFolder = dataFolder;
	}
	
	protected File dataFolder = null;
	protected Connection connection = null;
	protected String schema = null;
}