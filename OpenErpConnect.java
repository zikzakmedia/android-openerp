/*
 * OpenERP, Open Source Management Solution
 * Copyright (c) 2012 Zikzakmedia S.L. (http://zikzakmedia.com) All Rights Reserved.
 *               Enric Caumons Gou <caumons@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import org.xmlrpc.android.XMLRPCClient;
import org.xmlrpc.android.XMLRPCException;

import android.content.ContentValues;
import android.util.Log;

/**
 * This class provides access to basic methods in OpenObject, so you can use
 * them from an Android device. The operations supported are: <br>
 * - login <br>
 * - create <br>
 * - search <br>
 * - read <br>
 * - write <br>
 * - unlink <br>
 * - browse <br>
 * - call (This is a generic method to call whatever you need) <br>
 * You can extend OpenErpConnect to implement more specific methods of your need.
 * 
 * @author Enric Caumons Gou <caumons@gmail.com>
 * */
public class OpenErpConnect {
    
    protected String mServer;
    protected Integer mPort;
    protected String mDatabase;
    protected String mUserName;
    protected String mPassword;
    protected Integer mUserId;
    protected URL mUrl;
    
    protected static final String CONNECTOR_NAME = "OpenErpConnect";
    
    /** You should not use the constructor directly, use connect() instead */
    protected OpenErpConnect(String server, Integer port, String db, String user, String pass, Integer id) throws MalformedURLException {
        mServer = server;
        mPort = port;
        mDatabase = db;
        mUserName = user;
        mPassword = pass;
        mUserId = id;
        mUrl = new URL("http", server, port, "/xmlrpc/object");
    }
    
    /** @return An OpenErpConnect instance, which you will use to call the methods. */
    public static OpenErpConnect connect(String server, Integer port, String db, String user, String pass) {
        return login(server, port, db, user, pass);
    }
    
    public static OpenErpConnect connect(ContentValues connectionParams) {
        return login(connectionParams);
    }
    
    /** @return true if the connection could be established, else returns false. The connection will not be stored */
    public static Boolean testConnection(String server, Integer port, String db, String user, String pass) {
        return login(server, port, db, user, pass) != null;
    }
    
    public static Boolean testConnection(ContentValues connectionParams) {
        return login(connectionParams) != null;
    }
    
    protected static OpenErpConnect login(ContentValues connectionParams) {
        return login(connectionParams.getAsString("server"), connectionParams.getAsInteger("port"),
                connectionParams.getAsString("database"), connectionParams.getAsString("username"),
                connectionParams.getAsString("password"));
    }
    
    protected static OpenErpConnect login(String server, Integer port, String db, String user, String pass) {
        OpenErpConnect connection = null;
        try {
            URL loginUrl = new URL("http", server, port, "/xmlrpc/common");
            XMLRPCClient client = new XMLRPCClient(loginUrl);
            Integer id = (Integer)client.call("login", db, user, pass);
            connection = new OpenErpConnect(server, port, db, user, pass, id);
        } catch (XMLRPCException e) {
            Log.d(CONNECTOR_NAME, e.toString());
        } catch (MalformedURLException e) {
            Log.d(CONNECTOR_NAME, e.toString());
        } catch (ClassCastException e) {
            Log.d(CONNECTOR_NAME, e.toString()); // Bad login or password
        }
        return connection;
    }
    
    /**
     * Creates a new record for the given model width the values supplied, if
     * you do not need the context, just pass null for it.
     * Remember: In order to add different types in a Collection use Object, e.g. <br>
     * <code>
     * HashMap<String, Object> values = new HashMap<String, Object>(); <br>
     * values.put("name", "hello"); <br>
     * values.put("number", 10); <br>
     * </code>
     * */
    public Long create(String model, HashMap<String, ?> values, HashMap<String, ?> context) {
        Long newObjectId = null;
        try {
            XMLRPCClient client = new XMLRPCClient(mUrl);
            newObjectId = ((Integer)client.call("execute", mDatabase, mUserId, mPassword, model, "create", values, context)).longValue();
        } catch (XMLRPCException e) {
            Log.d(CONNECTOR_NAME, e.toString());
        }
        return newObjectId;
    }
    
    public Long[] search(String model, Object[] conditions) {
        return search(model, false, 0, 0, null, false, conditions);
    }
    
    public Long[] search(String model, Boolean count, Object[] conditions) {
        return search(model, false, 0, 0, null, false, conditions);
    }
    
    public Long[] search(String model, Boolean count, Integer limit, String order, boolean reverseOrder, Object[] conditions) {
        return search(model, false, 0, limit, order, reverseOrder, conditions);
    }
    
    /**
     * If count is true the resulting array will only contain the number of matching ids.
     * You can pass new Object[0] to specify an empty list of conditions,
     * which will return all the ids for that model.
     * 
     * @return The ids of matching objects.
     * */
    public Long[] search(String model, Boolean count, Integer offset, Integer limit, String order, boolean reverseOrder, Object[] conditions) {
        Long[] result = null;
        try {
            XMLRPCClient client = new XMLRPCClient(mUrl);
            Vector<Object> parameters = new Vector<Object>(11);
            parameters.add(mDatabase);
            parameters.add(mUserId);
            parameters.add(mPassword);
            parameters.add(model);
            parameters.add("search");
            parameters.add(conditions);
            parameters.add(offset);
            parameters.add(limit);
            parameters.add(order);
            parameters.add(null);
            parameters.add(count);
            if (count) { // We just want the number of items
                result = new Long[] { ((Integer)client.call("execute", parameters)).longValue() };
            } else { // Returning the list of matching item id's
                Object[] responseIds = (Object[])client.call("execute", parameters);
                // In case no matching records were found, an empty list is returned by the ws
                // The ids are returned as Integer, but we want Long for better Android compatibility
                result = new Long[responseIds.length];
                for (int i = 0; i < responseIds.length; i++) {
                    result[i] = ((Integer)responseIds[i]).longValue();
                }
                if (reverseOrder) {
                    reverseArray(result);
                }
            }
        } catch (XMLRPCException e) {
            Log.d(CONNECTOR_NAME, e.toString());
        } catch (NullPointerException e) {
            Log.d(CONNECTOR_NAME, e.toString()); // Null response (should not happen)
        }
        return result;
     }
    
    /**
     * Each HashMap in the List contains the values for the specified fields for each
     * object in the ids (in the same order).
     * 
     * @param fields Specifying an empty fields array as: new String[0] will return all the fields
     * */
    @SuppressWarnings("unchecked")
    public List<HashMap<String, Object>> read(String model, Long[] ids, String[] fields) {
        List<HashMap<String, Object>> listOfFieldValues = null;
        try {
            XMLRPCClient client = new XMLRPCClient(mUrl);
            Object[] responseFields = (Object[])client.call("execute", mDatabase, mUserId, mPassword, model, "read", ids, fields);
            listOfFieldValues = new ArrayList<HashMap<String, Object>>(responseFields.length);
            for (Object objectFields : responseFields) {
                listOfFieldValues.add((HashMap<String, Object>)objectFields);
            }
        } catch (XMLRPCException e) {
            Log.d(CONNECTOR_NAME, e.toString());
        }
        return listOfFieldValues;
    }
    
    /** Used to modify an existing object. */
    public Boolean write(String model, Long[] ids, HashMap<String, ?> values, HashMap<String, ?> context) {
        Boolean writeOk = false;
        try {
            XMLRPCClient client = new XMLRPCClient(mUrl);
            writeOk = (Boolean)client.call("execute", mDatabase, mUserId, mPassword, model, "write", ids, values, context);
        } catch (XMLRPCException e) {
            Log.d(CONNECTOR_NAME, e.toString());
        }
        return writeOk;
    }
    
    /** A method to delete the matching records width the ids given */
    public Boolean unlink(String model, Long[] ids) {
        Boolean unlinkOk = false;
        try {
            XMLRPCClient client = new XMLRPCClient(mUrl);
            unlinkOk = (Boolean)client.call("execute", mDatabase, mUserId, mPassword, model, "unlink", ids);
        } catch (XMLRPCException e) {
            Log.d(CONNECTOR_NAME, e.toString());
        }
        return unlinkOk;
    }
    
    /**
     * The result is stored in the parameter List<E> resultList. The parameter
     * modelClass should look like: MyClass.class Do not expect
     * to use it as in the native method. You will not jump from one model to
     * another just accessing the foreign field! But it is easier to work width
     * E instances than HashMaps ;)
     * The class E MUST define a public constructor with one parameter of type HashMap,
     * which will initialize the attributes width the values inside the
     * Hashmap width the keys corresponding to the fields supplied.
     * It is recommended no to hardcode the fields, instead, program a public
     * static method such as getAtrributeNames() in E that returns a List<String>
     * width the attribute names in the OpenERP table, which match the
     * attributes defined in the class.
     * You can extend classes and call the parent's getAtrributeNames() to
     * add() the new attributes (as it is a List<String>). Also, you can call
     * the super constructor and populate just the new attributes. This may
     * be useful for modules in OpenERP which add fields in existing models
     * e.g. module MyModule adds the field my_module_field to res.partner,
     * so you could define the classes ResPartner and ResPartnerMyModule,
     * if needed.
     * You can pass extras, which in turn will be received by the Class constructor
     * in the form of "extra_0", "extra_1"... in the HashMap
     * */
    public <E> void browse(String model, Class<E> modelClass, Long[] ids, List<String> fields, List<E> resultList, Object...extras) throws OpenErpConnectException {
        List<HashMap<String, Object>> listOfFieldValues = read(model, ids, fields.toArray(new String [fields.size()]));
        if (listOfFieldValues != null) {
            try {
                Constructor<E> constructor = modelClass.getConstructor(HashMap.class);
                for (HashMap<String, Object> objectHashmap : listOfFieldValues) {
                    for (int numParam = 0; numParam < extras.length; numParam++) {
                        objectHashmap.put("extra_"+numParam, extras[numParam]);
                    }
                    resultList.add(constructor.newInstance(objectHashmap));
                }
            } catch (SecurityException e) {
                Log.d(CONNECTOR_NAME, e.toString());
                throw new OpenErpConnectException(e.toString());
            } catch (NoSuchMethodException e) {
                Log.d(CONNECTOR_NAME, e.toString());
                throw new OpenErpConnectException(e.toString());
            } catch (IllegalArgumentException e) {
                Log.d(CONNECTOR_NAME, e.toString());
                throw new OpenErpConnectException(e.toString());
            } catch (InstantiationException e) {
                Log.d(CONNECTOR_NAME, e.toString());
                throw new OpenErpConnectException(e.toString());
            } catch (IllegalAccessException e) {
                Log.d(CONNECTOR_NAME, e.toString());
                throw new OpenErpConnectException(e.toString());
            } catch (InvocationTargetException e) {
                Log.d(CONNECTOR_NAME, e.toString());
                throw new OpenErpConnectException(e.toString());
            }
        } else {
            throw new OpenErpConnectException(OpenErpConnectException.ERROR_READ);
        }
    }
    
    /**
     * This is a generic method to call any WS.
     * @param parameters Each one of the Objects can be one object instance, array or List... depending on the WS called.
     * */
    public Object call(String model, String method, Object...parameters) {
        Object response = null;
        try {
            Vector<Object> paramsVector = new Vector<Object>(6);
            paramsVector.add(mDatabase);
            paramsVector.add(mUserId);
            paramsVector.add(mPassword);
            paramsVector.add(model);
            paramsVector.add(method);
            for (Object parameter : parameters) {
                paramsVector.add(parameter);
            }
            XMLRPCClient client = new XMLRPCClient(mUrl);
            response = client.call("execute", paramsVector);
        } catch (XMLRPCException e) {
            Log.d(CONNECTOR_NAME, e.toString());
        }
        return response;
    }
    
    /**
     * This utility method reverses the order of the Long elements (ids) in the array. Used to implement
     * reverse ordering. */
    public void reverseArray(Long[] array) {
        int minIndex = 0;
        int maxIndex = array.length-1;
        long minValue;
        while (minIndex < maxIndex) {
            minValue = array[minIndex];
            array[minIndex] = array[maxIndex];
            array[maxIndex] = minValue;
            minIndex++;
            maxIndex--;
        }
    }
    
    /**
     * @return String representation of the OpenErpConnection instance, good for
     * debugging purposes. You can comment the password if you want.
     * */
    public String toString() {
        StringBuilder stringConn = new StringBuilder();
        stringConn.append("server: " + mServer + "\n");
        stringConn.append("port: " + mPort + "\n");
        stringConn.append("database: " + mDatabase + "\n");
        stringConn.append("user: " + mUserName + "\n");
        stringConn.append("password: " + mPassword + "\n");
        stringConn.append("id: " + mUserId + "\n");
        return stringConn.toString();
    }
    
    /**
     * As Java does not support output parameters; to control whether an Exception occurred in
     * browse() method, we create this class, so the caller does not have to deal with so different
     * kind of exceptions. Instead, it will be notified if one (any kind) occurred.<br>
     * We can not assign the output parameter to null, because it is passed as a reference by value,
     * so the changes to the reference itself outside the method will not be seen; we can just
     * modify the object, not the memory position it is pointing.<br>
     * The rest of methods just return null in case of Exception, because it is more agile, so you
     * do not have to use try catch every time. However, if you wish, you can use this class
     * with the rest of methods.
     */
    public static class OpenErpConnectException extends Exception {
        
        private static final String ERROR_READ = "read() method returned unexpected null value";

        /** Required because Exception implements Serializable interface */
        private static final long serialVersionUID = 1L;
        
        public OpenErpConnectException(String message) {
            super(message);
        }
    }
}
