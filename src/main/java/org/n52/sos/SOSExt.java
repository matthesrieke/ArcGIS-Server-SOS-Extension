/*
 * Copyright (C) 2013
 * by 52 North Initiative for Geospatial Open Source Software GmbH
 * 
 * Contact: Andreas Wytzisk
 * 52 North Initiative for Geospatial Open Source Software GmbH
 * Martin-Luther-King-Weg 24
 * 48155 Muenster, Germany
 * info@52north.org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.n52.sos;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

import org.n52.om.observation.MultiValueObservation;
import org.n52.om.sampling.Feature;
import org.n52.oxf.valueDomains.time.ITimePosition;
import org.n52.oxf.valueDomains.time.TimeFactory;
import org.n52.sos.dataTypes.ObservationOffering;
import org.n52.sos.dataTypes.Procedure;
import org.n52.sos.dataTypes.ServiceDescription;
import org.n52.sos.db.AccessGDB;
import org.n52.util.ExceptionSupporter;

import com.esri.arcgis.carto.IMapServerDataAccess;
import com.esri.arcgis.interop.AutomationException;
import com.esri.arcgis.interop.extn.ArcGISExtension;
import com.esri.arcgis.interop.extn.ServerObjectExtProperties;
import com.esri.arcgis.server.IServerObjectExtension;
import com.esri.arcgis.server.IServerObjectHelper;
import com.esri.arcgis.server.SOAPRequestHandler;
import com.esri.arcgis.server.json.JSONArray;
import com.esri.arcgis.server.json.JSONObject;
import com.esri.arcgis.system.IObjectConstruct;
import com.esri.arcgis.system.IPropertySet;
import com.esri.arcgis.system.IRESTRequestHandler;
import com.esri.arcgis.system.ServerUtilities;

/**
 * The main class of this ArcGIS Server Object Extension (SOE).
 * 
 * @author <a href="mailto:broering@52north.org">Arne Broering</a>
 */
@ArcGISExtension
@ServerObjectExtProperties(
        displayName = "An_SOS_extension_for_ArcGIS_Server",
        description = "An_SOS_extension_for_ArcGIS_Server"
        )
public class SOSExt extends SOAPRequestHandler 
implements IServerObjectExtension, IObjectConstruct, ISosTransactionalSoap, IRESTRequestHandler {

    private static final long serialVersionUID = 1L;

    private IMapServerDataAccess mapServerDataAccess;

    public Logger LOGGER = Logger.getLogger(SOSExt.class.getName());
    
    protected AccessGDB geoDB;
    
    //private ObservationGDBInserter geoDBInserter;
    
    private String urlSosExtension;
    private String sosTitle;
    private String sosDescription;
    private String sosKeywords;
    private String sosProviderName;
    private String sosProviderSite;
    private String sosContactPersonName;
    private String sosContactPersonPosition;
    private String sosContactPersonPhone;
    private String sosContactPersonFax;
    private String sosContactPersonAddress;
    private String sosContactPersonCity;
    private String sosContactPersonAdminArea;
    private String sosContactPersonPostalCode;
    private String sosContactPersonCountry;
    private String sosContactPersonEmail;
    
    
    /**
     * constructs a new server object extension
     * 
     * @throws Exception
     */
    public SOSExt() throws Exception {
        super();
    }

    /*************************************************************************************
     * IServerObjectExtension methods:
     *************************************************************************************/

    /**
     * init() is called once, when the instance of the SOE is created.
     */
    public void init(IServerObjectHelper soh) throws IOException, AutomationException
    {
        LOGGER.info("Start initializing SOE");
        
        this.mapServerDataAccess = (IMapServerDataAccess) soh.getServerObject();
        
        LOGGER.info(this.getClass().getName() + " initialized.");
    }

    /**
     * shutdown() is called once when the Server Object's context is being shut
     * down and is about to go away.
     */
    public void shutdown() throws IOException, AutomationException
    {
        /*
         * The SOE should release its reference on the Server Object Helper.
         */
        LOGGER.info("Shutting down " + this.getClass().getName() + " SOE.");

        this.mapServerDataAccess = null;

        // TODO: make sure all references are being cut.
    }

    /*************************************************************************************
     * IConstructObject methods:
     *************************************************************************************/
    /**
     * construct() is called only once, when the SOE is created, after IServerObjectExtension.init() 
     * is called. This method hands back the configuration properties for the SOE as a property set.
     * You should include any expensive initialization logic for your SOE within your implementation 
     * of construct().  
     */
    public void construct(IPropertySet propertySet) throws IOException {
        
        // TODO --> read in maxNumOfResults from Manager
        
        try {
            LOGGER.info("Reading properties...");
            
            String specifiedUrlSosExtension = (String) propertySet.getProperty("urlSosExtension");
            // cut off '/' if necessary:
            if(specifiedUrlSosExtension.charAt(specifiedUrlSosExtension.length() - 1) == '/') {
                this.urlSosExtension = specifiedUrlSosExtension.substring(0, specifiedUrlSosExtension.length() - 1);
            }
            else {
                this.urlSosExtension = specifiedUrlSosExtension;
            }
            
            this.sosTitle = (String) propertySet.getProperty("title");
            this.sosDescription = (String) propertySet.getProperty("description");
            this.sosKeywords = (String) propertySet.getProperty("keywords");
            this.sosProviderName = (String) propertySet.getProperty("providerName");        
            this.sosProviderSite = (String) propertySet.getProperty("providerSite");
            this.sosContactPersonName = (String) propertySet.getProperty("contactPersonName");
            this.sosContactPersonPosition = (String) propertySet.getProperty("contactPersonPosition");
            this.sosContactPersonPhone = (String) propertySet.getProperty("contactPersonPhone");
            this.sosContactPersonFax = (String) propertySet.getProperty("contactPersonFax");
            this.sosContactPersonAddress = (String) propertySet.getProperty("contactPersonAddress");
            this.sosContactPersonCity = (String) propertySet.getProperty("contactPersonCity");
            this.sosContactPersonAdminArea = (String) propertySet.getProperty("contactPersonAdminArea");
            this.sosContactPersonPostalCode = (String) propertySet.getProperty("contactPersonPostalCode");
            this.sosContactPersonCountry = (String) propertySet.getProperty("contactPersonCountry");
            this.sosContactPersonEmail = (String) propertySet.getProperty("contactPersonEmail");
            
        } catch (Exception e) {
            LOGGER.severe("There was a problem while reading properties: \n" + e.getLocalizedMessage() + "\n" + ExceptionSupporter.createStringFromStackTrace(e));
            throw new IOException(e);
        }
     
        try {
            // create database access
            this.geoDB = new AccessGDB(this);
        } catch (Exception e) {
            LOGGER.severe("There was a problem while creating DB access: \n" + e.getLocalizedMessage() + "\n" + ExceptionSupporter.createStringFromStackTrace(e));
            throw new IOException(e);
        }
    }
    
    /*************************************************************************************
     * SOAP methods:
     *************************************************************************************/
    
    /**
     * @throws UnsupportedEncodingException 
     * 
     */
    @Override
    public String insertObservation(int offeringID,
            String phenomenonTime,
            int featureID,
            int observedPropertyID,
            int procedureID,
            float result) {
        
        try {
            String request = "";
            request += "offering = " + offeringID + "\n";
            request += "phenomenonTime = " + phenomenonTime + "\n";
            request += "feature = " + featureID + "\n";
            request += "observedProperty = " + observedPropertyID + "\n";
            request += "procedure = " + procedureID + "\n";
            request += "result = " + result + "\n";
            
            LOGGER.info("Incoming SOAP request parameters: " + request);
            
            ITimePosition t = (ITimePosition)TimeFactory.createTime(phenomenonTime);
            Date phenomenonTimeAsDate = t.getCalendar().getTime();
            
            int observationID = this.geoDB.getObservationInsert().insertObservation(offeringID, phenomenonTimeAsDate, featureID, observedPropertyID, procedureID, result);
            
            return "" + observationID;
        
        } catch (Exception e) {
            LOGGER.severe("There was a problem while answering the SOAP request: \n" + e.getLocalizedMessage() + "\n" + ExceptionSupporter.createStringFromStackTrace(e));

            return "There was a problem while answering the SOAP request: \n" + e.getLocalizedMessage() + "\n" + ExceptionSupporter.createStringFromStackTrace(e);
        }
    }
    
    
    /*************************************************************************************
     * IRESTRequestHandler methods:
     *************************************************************************************/

    /**
     * This method returns the resource hierarchy of a REST based SOE in JSON
     * format.
     */
    @Override
    public String getSchema() throws IOException, AutomationException
    {
        LOGGER.info("getSchema() is called...");

        return createSchema();
    }
    
    /**
     * Testable support method for getSchema().
     */
    protected static String createSchema() throws IOException, AutomationException {
        JSONObject arcGisSos = ServerUtilities.createResource("ArcGIS_SOS_Extension", "An_SOS_extension_for_ArcGIS_Server", false, false);
        JSONArray ogcOperationArray = new JSONArray();
        
        // create a schema object for the 'observations' resource:
        JSONObject observationsObject = ServerUtilities.createResource("observations", "description of observations resource", false, false);
        JSONArray observationsQueryOp = new JSONArray();
        observationsQueryOp.put(ServerUtilities.createOperation("query", "offering, observedProperty, procedure, featureOfInterest, spatialFilter, temporalFilter, where", "json", false));
        observationsObject.put("operations", observationsQueryOp);
        
//        observationsQueryOp.put(ServerUtilities.createOperation("diagram", "offering, observedProperty, procedure, featureOfInterest, spatialFilter, temporalFilter, where", "jpeg", false));
//        observationsObject.put("operations", observationsQueryOp);

        // create a schema object for the 'procedure' resource:
        JSONObject procedureObject = ServerUtilities.createResource("procedures", "description of procedure resource", true, true);
//        JSONArray proceduresQueryOp = new JSONArray();
//        proceduresQueryOp.put(ServerUtilities.createOperation("query", "procedure", "json", false));
//        procedureObject.put("operations", proceduresQueryOp);

        // create a schema object for the 'features' resource:
/*      JSONObject featuresObject = ServerUtilities.createResource("features", "description of features resource", false, false);
        JSONArray featuresQueryOp = new JSONArray();
        featuresQueryOp.put(ServerUtilities.createOperation("query", "feature, observedProperty, procedure, spatialFilter", "json", false));
        featuresObject.put("operations", featuresQueryOp);
*/
        
        // create a schema object for the GetCapabilities operation:
        ogcOperationArray.put(ServerUtilities.createOperation("GetCapabilities", "service, request", "json, xml", false));
        
        // create a schema object for the DescribeSensor operation:
        ogcOperationArray.put(ServerUtilities.createOperation("GetObservation", "service, version, request, offering, observedProperty, procedure, featureOfInterest, namespaces, spatialFilter, temporalFilter, responseFormat", "json, xml", false));
        
        // create a schema object for the DescribeSensor operation:
        ogcOperationArray.put(ServerUtilities.createOperation("GetObservationByID", "service, version, request, observation, responseFormat", "json, xml", false));

/*
        // create a schema object for the GetFeatureOfInterest operation:
        ogcOperationArray.put(ServerUtilities.createOperation("GetFeatureOfInterest", "service, version, request, featureOfInterest, observedProperty, procedure, namespaces, spatialFilter", "json, xml", false));
               
        // create a schema object for the DescribeSensor operation:
        ogcOperationArray.put(ServerUtilities.createOperation("DescribeSensor", "service, version, request, procedure, procedureDescriptionFormat", "json, xml", false));
*/      
        
        // include all resource objects into 'resources' array:
        JSONArray resourceArray = new JSONArray();
        resourceArray.put(observationsObject);
        resourceArray.put(procedureObject);
/*        resourceArray.put(featuresObject); */
        arcGisSos.put("resources", resourceArray);
        arcGisSos.put("operations", ogcOperationArray);

        return arcGisSos.toString();
    }
    
    /**
     * This method handles REST requests by determining whether an operation or
     * resource has been invoked and then forwards the request to appropriate
     * methods.
     * 
     * @param capabilities
     *            The capabilities supported by the SOE. An admin can choose
     *            which capabilities are enabled on a particular SOE (in ArcGIS
     *            Manager or ArcCatalog), based on certain criteria such as
     *            security roles. This list of allowed capabilities is then sent
     *            to this method, at runtime, as a comma separated list.
     * @param resourceName
     *            Name of the resource being addressed relative to the root SOE
     *            resource. If empty, its assumed that root resource is being
     *            addressed. E.g.: "procedures/mysensor123". For resource
     *            requests, the operationName parameter of this method will be
     *            an empty string ("").
     * @param operationName
     *            Name of the operation being invoked. If empty, description of
     *            resource is returned.
     * @param operationInput
     *            Input parameters to the operation specified by operationName
     *            parameter, encoded as a JSON formatted string. The REST
     *            handler coerces the input parameters of an operation into a
     *            JSON string. The request parameter names become the JSON
     *            property names. The values are attempted to be coerced to
     *            valid JSON types - numbers, booleans, JSON objects, JSON
     *            arrays. If they cannot be coerced to any of the types
     *            mentioned above, they'll be treated as strings.
     * @param outputFormat
     *            OutputFormat of operation. The value of the format parameter f
     *            of the REST API.
     */
    @Override
    public byte[] handleRESTRequest(String capabilities,
            String resourceName,
            String operationName,
            String operationInput,
            String outputFormat,
            String requestProperties,
            String[] responseProperties) throws IOException, AutomationException
    {
//        LOGGER.info("Starting to handle REST request...");
//        LOGGER.info("capabilities: " + capabilities);
//        LOGGER.info("resourceName: " + resourceName);
//        LOGGER.info("operationName: " + operationName);
//        LOGGER.info("operationInput: " + operationInput);
//        LOGGER.info("outputFormat: " + outputFormat);
//        LOGGER.info("requestProperties: " + requestProperties);

        try {
            // if no operationName is specified send description of specified
            // resource
            if (operationName.length() == 0) {
                return getResource(resourceName, operationInput);
            } else {
                
                if (geoDB == null) {
                    throw new RuntimeException ("Database connection null.");
                }
                
                // extract operation input parameters to Map:
                JSONObject inputObject = new JSONObject(operationInput);

                // handle: observations/query
                if (resourceName.equals("observations") && operationName.equalsIgnoreCase("query")) {
                    return invokeObservationQueryOperation(inputObject, outputFormat, responseProperties);
                }
                /*
                // handle: observations/diagram
                else if (resourceName.equals("observations") && operationName.equalsIgnoreCase("diagram")) {
                    return invokeObservationDiagramOperation(inputObject, outputFormat, responseProperties);
                }
                */

                // handle: features/query
                else if (resourceName.equals("features") && operationName.equalsIgnoreCase("query")) {
                    return invokeFeatureQueryOperation(inputObject);
                }

                // handle: procedures/query
                else if (resourceName.equals("procedures") && operationName.equalsIgnoreCase("query")) {
                    return invokeProcedureQueryOperation(inputObject);
                }
                
                // handle: /GetCapabilities
                else if (operationName.equalsIgnoreCase("GetCapabilities")) {
                    return new GetCapabilitiesOperationHandler(this.urlSosExtension).invokeOGCOperation(geoDB, inputObject, responseProperties);
                }

                // handle: /GetObservation
                else if (operationName.equalsIgnoreCase("GetObservation")) {
                    return new GetObservationOperationHandler(this.urlSosExtension).invokeOGCOperation(geoDB, inputObject, responseProperties);
                }
                
                // handle: /GetObservationByID
                else if (operationName.equalsIgnoreCase("GetObservationByID")) {
                    return new GetObservationByIDOperationHandler(this.urlSosExtension).invokeOGCOperation(geoDB, inputObject, responseProperties);
                }
                
                // handle: /DescribeSensor
                else if (operationName.equalsIgnoreCase("DescribeSensor")) {
                    return new DescribeSensorOperationHandler(this.urlSosExtension).invokeOGCOperation(geoDB, inputObject, responseProperties);
                }
                
                // handle: /GetFeatureOfInterest
                else if (operationName.equalsIgnoreCase("GetFeatureOfInterest")) {
                    return new GetFeatureOfInterestOperationHandler(this.urlSosExtension).invokeOGCOperation(geoDB, inputObject, responseProperties);
                }
                else {
                    throw new Exception("Operation '" + operationName + "' on resource '" + resourceName + "' not supported.");
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error while handle REST request: \n" + e.getLocalizedMessage() + "\n" + ExceptionSupporter.createStringFromStackTrace(e));
            
            // send out error:
            return ServerUtilities.sendError(3, "An exception occurred: " + e.toString(), ExceptionSupporter.createStringArrayFromStackTrace(e.getStackTrace())).getBytes("utf-8");
        }
    }

    
    /*************************************************************************************
     * Private, supporting & helper methods:
     *************************************************************************************/

    /*
    protected byte[] invokeObservationDiagramOperation(JSONObject inputObject,
            String outputFormat,
            String[] responseProperties) throws Exception
    {
        LOGGER.info("Start observation/diagram operation.");

        String[] offerings = null;
        if (inputObject.has("offering")) {
            offerings = inputObject.getString("offering").split(",");
        }
        String[] featuresOfInterest = null;
        if (inputObject.has("featureOfInterest")) {
            featuresOfInterest = inputObject.getString("featureOfInterest").split(",");
        }
        String[] observedProperties = null;
        if (inputObject.has("observedProperty")) {
            observedProperties = inputObject.getString("observedProperty").split(",");
        }
        String[] procedures = null;
        if (inputObject.has("procedure")) {
            procedures = inputObject.getString("procedure").split(",");
        }
        String spatialFilter = null;
        if (inputObject.has("spatialFilter")) {
            spatialFilter = inputObject.getString("spatialFilter");
        }
        String temporalFilter = null;
        if (inputObject.has("temporalFilter")) {
            temporalFilter = inputObject.getString("temporalFilter");
        }
        String where = null;
        if (inputObject.has("where")) {
            where = inputObject.getString("where");
        }

        GenericObservationCollection observations = this.geoDB.getObservations(offerings, featuresOfInterest, observedProperties, procedures, spatialFilter, temporalFilter, where);

        BufferedImage img = new TimeSeriesChartRenderer4xPhenomenons().createChartImage(observations);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(out);
        JPEGEncodeParam param = encoder.getDefaultJPEGEncodeParam(img);
        param.setQuality(1, false);
        encoder.encode(img, param);

        responseProperties = new String[] { "{\"Content-Type\" : \"image/jpeg\"}" };
        responseProperties = new String[]{"{\"Content-Disposition\" : inline; filename=\"sos-image.jpeg\"}"};

        return out.toByteArray();
    }
    */
    
    protected byte[] invokeObservationQueryOperation(JSONObject inputObject,
            String outputFormat,
            String[] responseProperties) throws Exception
    {
        LOGGER.info("Start observation query.");

        String[] offerings = null;
        if (inputObject.has("offering")) {
            offerings = inputObject.getString("offering").split(",");
        }
        String[] featuresOfInterest = null;
        if (inputObject.has("featureOfInterest")) {
            featuresOfInterest = inputObject.getString("featureOfInterest").split(",");
        }
        String[] observedProperties = null;
        if (inputObject.has("observedProperty")) {
            observedProperties = inputObject.getString("observedProperty").split(",");
        }
        String[] procedures = null;
        if (inputObject.has("procedure")) {
            procedures = inputObject.getString("procedure").split(",");
        }
        String spatialFilter = null;
        if (inputObject.has("spatialFilter")) {
            spatialFilter = inputObject.getString("spatialFilter");
        }
        String temporalFilter = null;
        if (inputObject.has("temporalFilter")) {
            temporalFilter = inputObject.getString("temporalFilter");
        }
        String where = null;
        if (inputObject.has("where")) {
            where = inputObject.getString("where");
        }

        Map<String, MultiValueObservation> observations = this.geoDB.getObservationAccess().getObservations(offerings, featuresOfInterest, observedProperties, procedures, spatialFilter, temporalFilter, where);

        JSONObject json = JSONObservationEncoder.encodeObservations(observations);
        return json.toString().getBytes("utf-8");
    }

    protected byte[] invokeFeatureQueryOperation(JSONObject inputObject) throws Exception
    {
        LOGGER.info("Start feature query.");

        JSONObject json = null;

        String[] features = null;
        if (inputObject.has("feature")) {
            features = inputObject.getString("feature").split(",");
        }
        String[] observedProperties = null;
        if (inputObject.has("observedProperty")) {
            observedProperties = inputObject.getString("observedProperty").split(",");
        }
        String[] procedures = null;
        if (inputObject.has("procedure")) {
            procedures = inputObject.getString("procedure").split(",");
        }
        String spatialFilter = null;
        if (inputObject.has("spatialFilter")) {
            spatialFilter = inputObject.getString("spatialFilter");
        }
        Collection<Feature> fois = this.geoDB.getFeatureAccess().getFeaturesOfInterest(features, observedProperties, procedures, spatialFilter);
        json = JSONEncoder.encodeSamplingFeatures(fois);

        return json.toString().getBytes("utf-8");
    }

    protected byte[] invokeProcedureQueryOperation(JSONObject inputObject) throws Exception
    {
        LOGGER.info("Start procedures query.");

        JSONObject json = null;

        String[] procedures = null;
        if (inputObject.has("procedure")) {
            procedures = inputObject.getString("procedure").split(",");
        }
        Collection<Procedure> proceduresColl = this.geoDB.getProcedureAccess().getProcedures(procedures);
        
        /*
        String returnIdsOnly = null;
        if (inputObject.has("returnIdsOnly")) {
            returnIdsOnly = inputObject.getString("returnIdsOnly");
            if (returnIdsOnly != null && Boolean.valueOf(returnIdsOnly)) {
                json = JSONEncoder.encodeProcedureIDs(proceduresColl);
            }
        }
        */

        if (json == null) {
            json = JSONEncoder.encodeProcedures(proceduresColl);
        }

        return json.toString().getBytes("utf-8");
    }
    
    
    
    

    /**
     * Returns JSON representation of specified resource.
     * 
     * @param resourceName
     *            Name of the resource being addressed relative to the root SOE
     *            resource. If empty, its assumed that root resource is being
     *            addressed. E.g.: "procedures/mysensor123".
     * 
     * @return JSON representation of specified resource as a byte[].
     */
    public byte[] getResource(String resourceName,
            String operationInput) throws Exception
    {
        // TODO consider operationInput

        LOGGER.info("getResource() is called.");

        if (geoDB == null) {
            throw new Exception("Database access object not instantiated.");
        }

        // this.serverLog.addMessage(1, 8000, "getResource() is called.");

        JSONObject json = null;

        // root resource is accessed:
        if (resourceName.equalsIgnoreCase("") || resourceName.length() == 0) {
            ServiceDescription serviceDesc = geoDB.getServiceDescription();
            json = JSONEncoder.encodeServiceDescription(serviceDesc);
        }

        else if (resourceName.matches("observations")) {
            Collection<ObservationOffering> offerings = geoDB.getOfferingAccess().getNetworksAsObservationOfferings();
            json = JSONEncoder.encodeObservationOfferings(offerings);
        }

        // handle queries for procedures:
        else if (resourceName.matches("procedures")) {
            Collection<Procedure> procedureArray = geoDB.getProcedureAccess().getProcedures(null);
            json = JSONEncoder.encodeProcedureIDs(procedureArray);
        }
        
        else if (resourceName.matches("procedures/.+")) {
            String procedureID = resourceName.split("/")[1];
//            LOGGER.info("Procedure requested: '" + procedureID + "'");

            Collection<Procedure> proceduresFromDB = geoDB.getProcedureAccess().getProcedures(new String[] { procedureID });
//            LOGGER.info("Count of procedures returned from DB: " + proceduresFromDB.size());

            if (proceduresFromDB.size() == 1) {
                Procedure p = proceduresFromDB.iterator().next();
                json = JSONEncoder.encodeProcedure(p);
            } else if (proceduresFromDB.size() > 1) {
                json = JSONEncoder.encodeProcedures(proceduresFromDB);
            } else if (proceduresFromDB.size() == 0) {
                throw new Exception("Procedure with name: '" + procedureID + "' not in DB.");
            }
        }

        // handle queries for features:
        else if (resourceName.matches("features")) {
            Collection<Feature> fois = geoDB.getFeatureAccess().getFeaturesOfInterest(null, null, null, null);
            json = JSONEncoder.encodeSamplingFeaturesIDs(fois);
        }
        /*
        else if (resourceName.matches("features/.+")) {
            String foiName = resourceName.split("/")[1];
            LOGGER.info("Feature requested: '" + foiName + "'");

            Collection<SpatialSamplingFeature> foisFromDB = geoDB.getFeaturesOfInterest(new String[] { foiName }, null, null, null);
            LOGGER.info("Count of features returned from DB: " + foisFromDB.size());

            if (foisFromDB.size() == 1) {
                SpatialSamplingFeature foi = foisFromDB.iterator().next();
                json = JSONEncoder.encodeSamplingFeature(foi);
            } else if (foisFromDB.size() > 1) {
                json = JSONEncoder.encodeSamplingFeatures(foisFromDB);
            } else if (foisFromDB.size() == 0) {
                throw new Exception("Feature with name: '" + foiName + "' not in DB.");
            }
        }
        */

        return json.toString().getBytes("utf-8");
    }


    //
    // ------------------------ getters -----------------------------
    //
    

    public IMapServerDataAccess getMapServerDataAccess()
    {
        return this.mapServerDataAccess;
    }
    
    public String getUrlSosExtension()
    {
        return urlSosExtension;
    }

    public String getSosTitle()
    {
        return sosTitle;
    }

    public String getSosDescription()
    {
        return sosDescription;
    }

    public String getSosKeywords()
    {
        return sosKeywords;
    }

    public String getSosProviderName()
    {
        return sosProviderName;
    }

    public String getSosProviderSite()
    {
        return sosProviderSite;
    }

    public String getSosContactPersonName()
    {
        return sosContactPersonName;
    }

    public String getSosContactPersonPosition()
    {
        return sosContactPersonPosition;
    }

    public String getSosContactPersonPhone()
    {
        return sosContactPersonPhone;
    }

    public String getSosContactPersonFax()
    {
        return sosContactPersonFax;
    }

    public String getSosContactPersonAddress()
    {
        return sosContactPersonAddress;
    }

    public String getSosContactPersonCity()
    {
        return sosContactPersonCity;
    }

    public String getSosContactPersonAdminArea()
    {
        return sosContactPersonAdminArea;
    }

    public String getSosContactPersonPostalCode()
    {
        return sosContactPersonPostalCode;
    }

    public String getSosContactPersonCountry()
    {
        return sosContactPersonCountry;
    }

    public String getSosContactPersonEmail()
    {
        return sosContactPersonEmail;
    }
    
}

