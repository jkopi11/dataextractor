package com.kopinski.Test;

import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.client.model.*;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;

import static com.mongodb.client.model.Filters.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.lang.Runnable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


import java.sql.Timestamp;
import java.util.Date;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

//import javax.xml.parsers.DocumentBuilderFactory;
//import javax.xml.parsers.DocumentBuilder;

import org.apache.commons.io.FileUtils;

import org.bson.Document;
import org.bson.BsonBoolean;

import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;


public class App implements Runnable {
    
    private static final String collName = "dataSources";
	org.w3c.dom.Document dom;
    MongoCollection products;
    Timestamp ts;
    private final static ExecutorService executor = Executors.newCachedThreadPool();
    private final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
 
    public static void main(String[] args) {
        final Runnable updater = new Runnable() {
            public void run() { 
                for (int i = 0; i < 2; i++) {
                    Runnable update = new App();
                    executor.execute(update);
                }
                executor.shutdown();
            }
        };
        
        final ScheduledFuture<?> updateHandle =
            scheduler.scheduleAtFixedRate(updater, 5, 600, TimeUnit.SECONDS);
    }
    
    @Override
    public void run() {
        updateDataFromSource();
    }
    
    
    
    
    public void updateDataFromSource(){
        MongoClient mongoClient = new MongoClient( "localhost" , 27017 );
        MongoDatabase db = mongoClient.getDatabase("markable");
        MongoCollection coll = db.getCollection("dataSources");
        
        if (coll == null) {
            db.createCollection("dataSources");
            coll = db.getCollection("dataSources");
        }
        
        if (coll.count() == 0) {
            insertTest(coll);
        }
        
        products = db.getCollection("products");
            
        System.out.println(products.count());
        
        if (products == null) {
            db.createCollection("products");
            products = db.getCollection("products");
        }
        
        FindIterable<Document> iterable = coll.find(eq("name", "test1"));
        iterable.forEach(new Block<Document>() {
            @Override
            public void apply(final Document document) {
                getData(document.get("url").toString());
            }
        });
        
        
        mongoClient.close();
    }
   
    public void getData(String address) {
        try {
//            address = "http://localhost:9999/data/Rebecca_Minkoff_Ben_Minkoff-RebeccaMinkoff_com_Product_Catalog.xml";
            URL url = new URL(address);
            File destination = new File("rmpc.xml");
            FileUtils.copyURLToFile(url, destination);
            
            parseXmlFile(destination);
            
            cleanUp();
        
        } catch (Exception e){
            System.out.println(e);
        }
    }

    private void cleanUp(){
        Document update = new Document("$set",new Document("active",false));
        if (ts != null) {
            try {
                UpdateResult ur = products.updateMany(ne("lastUpdate", ts),update);
                System.out.println(ur.getMatchedCount());
            } catch (Exception e){
                e.printStackTrace();
            }
            
        }  
    }
    
    private void parseXmlFile(File file){
        
        try {

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            java.util.Date date= new java.util.Date();
            ts = new Timestamp(date.getTime());
            DefaultHandler handler = new DefaultHandler() {

                Document document;
                String curNode;
                String upc;
                String value;
                boolean child;

                public void startElement(String uri, String localName,String qName,
                            Attributes attributes) throws SAXException {

                    if (qName.equalsIgnoreCase("product")) {
                        document = new Document()
                            .append("lastUpdate",ts.toString())
                            .append("active",new BsonBoolean(true));
                        child = false;
                    } else {
                        child = true;
                        curNode = qName; 
                    }
                }

                public void endElement(String uri, String localName,
                    String qName) throws SAXException {

                    if (qName.equalsIgnoreCase("product")) {
                        products.replaceOne(eq("upc",upc),document,(new UpdateOptions()).upsert(true));
                        System.out.println(products.count());
                    }

                }

                public void characters(char ch[], int start, int length) throws SAXException {
                    value = new String(ch, start, length);
                    if (child) {
                        if (curNode == "upc"){
                            upc = value;
                        }
                        document.append(curNode,value);
                    }
                }
            };
            
        
            saxParser.parse(file,handler);

        } catch (SAXParseException spe) {
            cleanUp();
            spe.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
   }

    
    public static boolean collectionExists(MongoDatabase db, final String collectionName) {
        MongoIterable<String> collectionNames = db.listCollectionNames();
        for (final String name : collectionNames) {
            if (name.equalsIgnoreCase(collectionName)) {
                return true;
            }
        }
        return false;
    }
    
    public static void insertTest(MongoCollection coll){
        Document doc = new Document().append("name", "MongoDB")
            .append("name", "test1")
            .append("url", "https://cl.ly/3d2U2G1C3r1E/download/Rebecca_Minkoff_Ben_Minkoff-RebeccaMinkoff_com_Product_Catalog.xml")
            .append("dataType", "xml")
            .append("idProperty","upc");
        coll.insertOne(doc);
        System.out.println(doc);
    }
}