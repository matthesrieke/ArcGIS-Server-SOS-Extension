/**
 * Copyright (C) 2012 52°North Initiative for Geospatial Open Source Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.n52.sos.db.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.n52.om.sampling.AQDSample;
import org.n52.om.sampling.Feature;
import org.n52.ows.InvalidParameterValueException;
import org.n52.sos.Constants;
import org.n52.sos.db.AccessGdbForFeatures;
import org.n52.util.CommonUtilities;
import org.n52.util.logging.Logger;

import com.esri.arcgis.geodatabase.Fields;
import com.esri.arcgis.geodatabase.ICursor;
import com.esri.arcgis.geodatabase.IQueryDef;
import com.esri.arcgis.geodatabase.IRow;
import com.esri.arcgis.geometry.Point;
import com.esri.arcgis.interop.AutomationException;

/**
 * @author <a href="mailto:broering@52north.org">Arne Broering</a>
 */
public class AccessGdbForFeaturesImpl implements AccessGdbForFeatures {
    
    static Logger LOGGER = Logger.getLogger(AccessGdbForFeaturesImpl.class.getName());

    private AccessGDBImpl gdb;

    public AccessGdbForFeaturesImpl(AccessGDBImpl accessGDB) {

        this.gdb = accessGDB;
    }
    
    /**
     * This method can be used to retrieve all features of interest complying to
     * the filter as specified by the parameters. The method basically reflects
     * the SOS:GetFeatureOfInterest() operation on Java level.
     * 
     * If one of the method parameters is <b>null</b>, it shall not be
     * considered in the query.
     * 
     * @param featuresOfInterest
     *            the names of requested features.
     * @param observedProperties
     *            the descriptions of observed properties.
     * @param procedures
     *            the unique IDs of procedures.
     * @param spatialfilter
     *            a spatial filter that shall be applied.
     * 
     * @return all features of interest from the Geodatabase which comply to the
     *         specified parameters.
     * @throws Exception
     */
    public Collection<Feature> getFeaturesOfInterest(
    		String[] featuresOfInterest,
            String[] observedProperties,
            String[] procedures,
            String spatialFilter) throws Exception
    {
    	
        List<Feature> features = new ArrayList<Feature>();
        IQueryDef queryDef = gdb.getWorkspace().createQueryDef();
       
        
        // set sub fields
        List<String> subFields = new ArrayList<String>();
        
        subFields.add(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_PK_FEATUREOFINTEREST));
        subFields.add(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_SHAPE));
        subFields.add(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_ID));
        subFields.add(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_RESOURCE));
        subFields.add(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_INLETHEIGHT));
        subFields.add(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_BUILDINGDISTANCE));
        subFields.add(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_KERBDISTANCE));
        
		/*
		 * The 'procedure' parameter of GetFOI can either be a
		 * NETWORK identifier or a PROCEDURE resource.
		 * 
		 * Hence, we have to check what they are first:
		 */
    	List<String> proceduresWhichAreNetworks = new ArrayList<String>();
    	List<String> proceduresWhichAreProcedures = new ArrayList<String>();
    	
        if (procedures != null) {
        	for (String procedure : procedures) {
        		if (gdb.getProcedureAccess().isNetwork(procedure)) {
        			proceduresWhichAreNetworks.add(procedure);
        		}
        		else if (gdb.getProcedureAccess().isProcedure(procedure)) {
        			proceduresWhichAreProcedures.add(procedure);
        		}
        	}
        	
        	/*
        	 * We only support the request of one kind of procedure per request:
        	 */
        	if (proceduresWhichAreNetworks.size() > 0 && proceduresWhichAreProcedures.size() > 0) {
        		throw new InvalidParameterValueException("The parameter 'PROCEDURE' can either contain NETWORK identifiers or PROCEDURE resource identifiers. A mix is unsupported.");
        	}
        	
        	if (proceduresWhichAreProcedures.size() > 0) {
        		subFields.add(gdb.concatTableAndField(Table.PROCEDURE, SubField.PROCEDURE_RESOURCE));
        	}
        	else if (proceduresWhichAreNetworks.size() > 0) {
        		subFields.add(gdb.concatTableAndField(Table.NETWORK, SubField.NETWORK_ID));
        	}
        }
        
        if (observedProperties != null) {
            subFields.add(gdb.concatTableAndField(Table.PROPERTY, SubField.PROPERTY_ID));
        }
        
        queryDef.setSubFields(gdb.createCommaSeparatedList(subFields));
        
		// Log out the query clause
        LOGGER.info("SELECT " + queryDef.getSubFields());


        
        // set tables
        List<String> tables = new ArrayList<String>();
        tables.add(Table.FEATUREOFINTEREST);
        if (procedures != null){
	        tables.add(Table.OBSERVATION);
	        
	        if (proceduresWhichAreProcedures.size() > 0) {
	        	tables.add(Table.PROCEDURE);	
	        }
	        else if (proceduresWhichAreNetworks.size() > 0) {
	        	tables.add(Table.SAMPLINGPOINT);
	        	tables.add(Table.STATION);
	        	tables.add(Table.NETWORK);
	        }
	    }
        
        if (observedProperties != null) {
        	tables.add(Table.OBSERVATION);
        	tables.add(Table.PROPERTY);        	
        }
        
        String tableList = gdb.createCommaSeparatedList(tables);
        queryDef.setTables(tableList);
        
        // Log out the query clause
        LOGGER.info("FROM " + queryDef.getTables());
        
        
        // create the where clause with joins and constraints
        StringBuilder whereClause = new StringBuilder();

        boolean isFirst = true;
        
        // joins
        if (observedProperties != null || procedures != null) {
	        whereClause.append(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_PK_FEATUREOFINTEREST) + " = " + 
	        				   gdb.concatTableAndField(Table.OBSERVATION, SubField.OBSERVATION_FK_FEATUREOFINTEREST));
	        isFirst = false;
        }
        
        if (observedProperties != null) {
        	isFirst = ifIsFirstAppendAND (whereClause, isFirst);
	        whereClause.append(gdb.concatTableAndField(Table.OBSERVATION, SubField.OBSERVATION_FK_PROPERTY) + " = " + 
	        				   gdb.concatTableAndField(Table.PROPERTY, SubField.PROPERTY_PK_PROPERTY));
        }

        if (procedures != null) {
        	if (proceduresWhichAreProcedures.size() > 0) {
        		isFirst = ifIsFirstAppendAND (whereClause, isFirst);
    	        whereClause.append(gdb.concatTableAndField(Table.OBSERVATION, SubField.OBSERVATION_FK_PROCEDURE) + " = " + 
    	        				   gdb.concatTableAndField(Table.PROCEDURE, SubField.PROCEDURE_PK_PROCEDURE));	
        	}
        	else if (proceduresWhichAreNetworks.size() > 0) {
        		isFirst = ifIsFirstAppendAND (whereClause, isFirst);
        		/*
        		 *	STATION.FK_NETWORK_GID = Network.pk_network AND
  					STATION.PK_STATION = SAMPLINGPOINT.FK_STATION AND
  					SAMPLINGPOINT.PK_SAMPLINGPOINT = Observation.fk_samplingpoint AND
  					Observation.fk_featureofinterest = FEATUREOFINTEREST.PK_FEATUREOFINTEREST
        		 */
        		whereClause.append(gdb.concatTableAndField(Table.NETWORK, SubField.NETWORK_PK_NETWOK));
        		whereClause.append(" = ");
        		whereClause.append(gdb.concatTableAndField(Table.STATION, SubField.STATION_FK_NETWORK_GID));        		
        		
        		whereClause.append(" AND ");
        		whereClause.append(gdb.concatTableAndField(Table.STATION, SubField.STATION_PK_STATION));
        		whereClause.append(" = ");
        		whereClause.append(gdb.concatTableAndField(Table.SAMPLINGPOINT, SubField.SAMPLINGPOINT_FK_STATION));
        		
        		whereClause.append(" AND ");
        		whereClause.append(gdb.concatTableAndField(Table.SAMPLINGPOINT, SubField.SAMPLINGPOINT_PK_SAMPLINGPOINT));
        		whereClause.append(" = ");
        		whereClause.append(gdb.concatTableAndField(Table.OBSERVATION, SubField.OBSERVATION_FK_SAMPLINGPOINT));
        		
        		whereClause.append(" AND ");
        		whereClause.append(gdb.concatTableAndField(Table.OBSERVATION, SubField.OBSERVATION_FK_FEATUREOFINTEREST));
        		whereClause.append(" = ");
        		whereClause.append(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_PK_FEATUREOFINTEREST));
        		
//        		whereClause.append(" AND (");
//        		/*
//        		 * AND Network.ID = 'NET-FI002A' ORDER BY PK_FEATUREOFINTEREST
//        		 */
//        		for (String network : proceduresWhichAreNetworks) {
//            		whereClause.append(gdb.concatTableAndField(Table.NETWORK, SubField.NETWORK_ID));
//            		whereClause.append(" = '");
//            		whereClause.append(network);
//            		whereClause.append("' OR ");
//				}
//        		whereClause.delete(whereClause.length() - 3, whereClause.length());
//        		whereClause.append(")");
        	}
        	
        }
        
        // build query for feature of interest
        if (featuresOfInterest != null) {
            isFirst = ifIsFirstAppendAND (whereClause, isFirst);
            whereClause.append(gdb.createOrClause(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_RESOURCE), featuresOfInterest));
        }

        // build query for observed properties
        if (observedProperties != null) {
        	isFirst = ifIsFirstAppendAND (whereClause, isFirst);
            whereClause.append(gdb.createOrClause(gdb.concatTableAndField(Table.PROPERTY, SubField.PROPERTY_ID), observedProperties));
        }

        // build query for procedures
        if (procedures != null) {
        	isFirst = ifIsFirstAppendAND (whereClause, isFirst);
        	
        	if (proceduresWhichAreProcedures.size() > 0) {
        		whereClause.append(gdb.createOrClause(gdb.concatTableAndField(Table.PROCEDURE, SubField.PROCEDURE_RESOURCE), procedures));
        	}
        	else if (proceduresWhichAreNetworks.size() > 0) {
        		whereClause.append(gdb.createOrClause(gdb.concatTableAndField(Table.NETWORK, SubField.NETWORK_ID), procedures));
        	}
        }

        // build query for spatial filter
        if (spatialFilter != null) {
        	Collection<String> featureList = gdb.queryFeatureIDsForSpatialFilter(spatialFilter);
            String[] featureArray = CommonUtilities.toArray(featureList);
            
            if (featureList.size() > 0) {
                // append the list of feature IDs:
            	isFirst = ifIsFirstAppendAND (whereClause, isFirst);
            	whereClause.append(gdb.createOrClause(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_ID), featureArray));
            } else {
                LOGGER.warn("The defined spatialFilter '" + spatialFilter + "' did not match any features in the database.");
            }
        }

        queryDef.setWhereClause(whereClause.toString());

        // Log out the query clause
        LOGGER.info("WHERE " + queryDef.getWhereClause());

        /*
         * check for exceeding the size limit
         */
        DatabaseUtils.assertMaximumRecordCount(tableList, whereClause.toString(), gdb);
        
        boolean shapeFromStations = false;
		/*
         * WORKAROUND for missing geometries/shapes
         */
        if (this.gdb.isResolveGeometriesFromStations()) {
        	StringBuilder isNullWhereClause = new StringBuilder();
        	isNullWhereClause.append(" AND ");
        	isNullWhereClause.append(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_SHAPE));
        	isNullWhereClause.append(" IS NULL");
        	
        	int count = DatabaseUtils.resolveRecordCount(queryDef.getTables(),
        			whereClause.toString().concat(isNullWhereClause.toString()),
        			gdb);
        	
        	if (count > 0) {
        		subFields.remove(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_SHAPE));
        		subFields.add(gdb.concatTableAndField(Table.STATION, SubField.STATION_SHAPE));
        		queryDef.setSubFields(gdb.createCommaSeparatedList(subFields));
        		
        		//add station table - might not be there
        		tables.add(Table.STATION);
        		tableList = gdb.createCommaSeparatedList(tables);
				queryDef.setTables(tableList);
        		
        		shapeFromStations = true;
        	}
        }
        
        // evaluate the database query
        ICursor cursor = queryDef.evaluate();

        // convert cursor entries to abstract observations
        Fields fields = (Fields) cursor.getFields();

        IRow row;
        int count = 0;
        while ((row = cursor.nextRow()) != null && count < gdb.getMaxNumberOfResults()) {
            count++;
            Feature feature = createFeature(row, fields, shapeFromStations);
            features.add(feature);
        }

        return features;
    }
    
    ///////////////////////////////
    /////////////////////////////// Helper Methods:
    ///////////////////////////////     
    
    /**
     * This method creates a {@link AQDSample} of a given {@link IRow} and it's {@link Fields}
     */
    protected AQDSample createFeature(IRow row, Fields fields, boolean shapeFromStations) throws AutomationException, IOException, URISyntaxException
    {	
        // gml identifier
        String gmlId = (String) row.getValue(fields.findField(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_ID)));
        
        // resource URI
        URI resourceUri = null;
        String resource = (String) row.getValue(fields.findField(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_RESOURCE)));
        if (resource != null) {
        	resourceUri = new URI(resource);
        }

        // local ID
        int localId = (Integer) row.getValue(fields.findField(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_PK_FEATUREOFINTEREST)));
        
        // shape
        Point point = null;
        Object shape;
		if (!shapeFromStations) {
        	shape = row.getValue(fields.findField(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_SHAPE)));
        }
        else {
        	shape = row.getValue(fields.findField(gdb.concatTableAndField(Table.STATION, SubField.STATION_SHAPE)));
        }
        
        if (shape instanceof Point) {
            point = (Point) shape;
        } else {
            LOGGER.warn("Shape of the feature '" + gmlId + "' is no point.");
        }
        
        // inletHeight
        Double inletHeight = (Double) row.getValue(fields.findField(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_INLETHEIGHT)));
        if (inletHeight == null) {
            inletHeight = Constants.FEATURE_INLET_HEIGHT;
        }
        
        // buildingDistance
        Double buildingDistance = (Double) row.getValue(fields.findField(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_BUILDINGDISTANCE)));
        if (buildingDistance == null) {
            buildingDistance = Constants.FEATURE_BUILDING_DISTANCE;
        }
        
        // kerbDistance
        Double kerbDistance = (Double) row.getValue(fields.findField(gdb.concatTableAndField(Table.FEATUREOFINTEREST, SubField.FEATUREOFINTEREST_KERBDISTANCE)));
        if (kerbDistance == null) {
            kerbDistance = Constants.FEATURE_KERB_DISTANCE;
        }

        return new AQDSample(resourceUri, gmlId, localId, null, null, null, point, null, inletHeight, buildingDistance, kerbDistance);
    }
    
    
    /**
     * helper method to reduce code length. Appends "AND" to WHERE clause if 'isFirst == false'.
     */
    private boolean ifIsFirstAppendAND (StringBuilder whereClauseParameterAppend, boolean isFirst) {
    	if (isFirst == false) {
    		whereClauseParameterAppend.append(" AND ");
    	}
    	else {
    		isFirst = false;
    	}
    	return isFirst;
    }
}
