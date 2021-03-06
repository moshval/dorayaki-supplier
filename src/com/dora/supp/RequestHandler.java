package com.dora.supp;

import java.sql.*;
import java.util.*;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class RequestHandler {
    static final String DB_USER = "postgres";
    static final String DB_PASS = "admin";
    static final String DB_URL = "jdbc:postgresql://localhost:5432/factorydb";
    static final int threshold = 10;

    Connection c;
    public RequestHandler(){
        try{
            Class.forName("org.postgresql.Driver");
            c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            System.out.println("Connected to DB");
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
    public ArrayList<Request> getRequests(){ // get pending requests
        ArrayList<Request> reqList = new ArrayList<Request>();
        try{
            String q = "SELECT * FROM request WHERE status='pending'";
            Statement stmt = c.createStatement();
            ResultSet rSet = stmt.executeQuery(q);
            while(rSet.next()){
                Request req = new Request();
                req.setReqid(rSet.getInt("request_id"));
                req.setDoraid(rSet.getInt("dora_id"));
                req.setQty(rSet.getInt("req_qty"));
                req.setStatus(rSet.getString("status"));
                reqList.add(req);
            }
            System.out.println("Yes");
        }
        catch(Exception e){
            e.printStackTrace();
        }
        finally{
            if(c!=null){
                try{
                    c.close();
                }
                catch(Exception e){
                    System.out.println("Failed to close");
                }
            }
        }
        return reqList;
    }

    public Request getReqOnId(int _reqid){ // get specific req w/ id = reqid
        Request req = new Request();
        try{
            String q = String.format("SELECT * from request WHERE request_id=%d",_reqid);
            Statement stmt = c.createStatement();
            ResultSet rSet = stmt.executeQuery(q);
            while(rSet.next()){
                req.setReqid(rSet.getInt("request_id"));
                req.setDoraid(rSet.getInt("dora_id"));
                req.setQty(rSet.getInt("req_qty"));
                req.setStatus(rSet.getString("status"));
            }
            System.out.println("Yes");
        }
        catch(Exception e){
            e.printStackTrace();
        }
        finally{
            if(c!=null){
                try{
                    c.close();
                }
                catch(Exception e){
                    System.out.println("Failed to close");
                }
            }
        }
        return req;
    }

    public boolean insertRequest(String dora,int qty,String ip,String ts,String epo){ // Insert req to log w/ rate limiter
        boolean retval = false;
        try{
            int dora_id = new DorayakiHandler().getDoraid(dora);
            String q = String.format("SELECT COUNT(*) AS rowcount FROM request_log WHERE ip='%s' AND epoint='%s' AND timestamp_req > NOW() - interval '5 minutes'",ip,epo);
            // Rate Limit : max 10 request from a specific ip to endpoint in last 5 mins
            Statement stmt = c.createStatement();
            ResultSet rSet = stmt.executeQuery(q);
            rSet.next();
            int count = rSet.getInt("rowcount");
            if(count < threshold){
                String insq1 = String.format("INSERT INTO request(dora_id,req_qty,status) VALUES (%d,%d,'%s') RETURNING request_id",dora_id,qty,"pending");
                ResultSet res = stmt.executeQuery(insq1);
                System.out.println("Insert to request Success");
                res.next();
                int reqid = res.getInt("request_id"); 
                String insq2 = String.format("INSERT INTO request_log(request_id,ip,timestamp_req,epoint) VALUES (%d,'%s','%s','%s')",reqid,ip,ts,epo);
                stmt.executeUpdate(insq2);
                System.out.println("Insert to request_log Success");
                retval = true;
                boolean send = this.sendRequest(dora, qty, ip, ts, epo);
                System.out.println(send);  
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        finally{
            if(c!=null){
                try{
                    c.close();
                }
                catch(Exception e){
                    System.out.println("Failed to close");
                }
            }
        }
        return retval;
    }
    public ArrayList<ArrayList<String>> getStatus(String ip){ //get status of all requests
        ArrayList<ArrayList<String>> reqList = new ArrayList<ArrayList<String>>();
        try{
            String q = String.format("SELECT timestamp_req,status from request,request_log WHERE request.request_id = request_log.request_id AND request_log.ip ='%s'",ip);
            Statement stmt = c.createStatement();
            ResultSet rSet = stmt.executeQuery(q);
            while(rSet.next()){
                ArrayList<String> temp = new ArrayList<String>();
                temp.add(rSet.getString("timestamp_req"));
                temp.add(rSet.getString("status"));
                reqList.add(temp);
            }
            System.out.println("Yes");
        }
        catch(Exception e){
            e.printStackTrace();
        }
        finally{
            if(c!=null){
                try{
                    c.close();
                }
                catch(Exception e){
                    System.out.println("Failed to close");
                }
            }
        }
        return reqList;
    }

    public boolean sendRequest(String dora,int qty,String ip,String ts,String epo){ //send detail request ke REST API node/backend pabrik 
        boolean retval = false;
        String build;
        try{
            URL url = new URL("http://localhost:5000/reqdor");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            String time = ts.replace(":", ".");
            if(qty > 0){
                build = "[From "+ip+" at "+time+"] Add "+String.valueOf(qty)+" "+dora+" to shop";
            }
            else{
                build = "[From "+ip+" at "+time+"] Remove "+String.valueOf((-1 * qty))+" "+dora+" from shop";
            }
            // System.out.println(build);
            // String sent = "{\"msg\":KUDA}";
            String sent = "{\"msg\":\""+build+"\"}";
            System.out.println(sent);
            OutputStream os = conn.getOutputStream();
            os.write(sent.getBytes());
            os.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(
				(conn.getInputStream())));

		    String output;
		    System.out.println("Output from Server .... \n");
		    while ((output = br.readLine()) != null) {
			    System.out.println(output);
		    }

		conn.disconnect();
            retval = true;
        }
        catch (MalformedURLException e) {

            e.printStackTrace();
    
          } catch (IOException e) {
    
            e.printStackTrace();
    
         }
         return retval;
    }

    // public static void main(String[] args) {
    //     RequestHandler rh = new RequestHandler();
    //     rh.sendRequest("blah",-2,"123.123.123","2021-11-22 06:45:10","reqdor");
    // }
}
