package org.n52.wps.io.datahandler.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import net.opengis.gml.BoundingShapeType;
import net.opengis.gml.FeaturePropertyType;
import net.opengis.gml.StringOrRefType;
import net.opengis.om.x10.AnyOrReferenceType;
import net.opengis.om.x10.ObservationCollectionDocument;
import net.opengis.om.x10.ObservationCollectionType;
import net.opengis.om.x10.ObservationType;
import net.opengis.om.x10.ProcessPropertyType;
import net.opengis.swe.x101.PhenomenonPropertyType;
import net.opengis.swe.x101.TimeObjectPropertyType;

import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.GeometryBuilder;
import org.geotools.referencing.CRS;
import org.n52.wps.io.data.IData;
import org.n52.wps.io.data.binding.complex.GTVectorDataBinding;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.primitive.Point;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to handle the parsing of SOS V1 O&M observations
 * 
 * @author Sebastian Clarke - Environment Systems 2015
 *
 */
public class SOS1BasicParser extends AbstractParser {
	private static Logger LOGGER = LoggerFactory.getLogger(SOS1BasicParser.class);
	
	protected SimpleFeatureType type;
	protected SimpleFeatureBuilder featureBuilder;

	public SOS1BasicParser() {
		super();
		supportedIDataTypes.add(GTVectorDataBinding.class);
	}
	
	@Override
	public IData parse(InputStream input, String mimeType, String schema) {
		XmlObject doc;
		try {
			doc = XmlObject.Factory.parse(input);
		} catch (XmlException e) {
			throw new IllegalArgumentException("Error parseing XML", e);
		} catch (IOException e) {
			throw new IllegalArgumentException("Error transferring XML", e);
		}
		return parseXML(doc);
	}
	
	protected GTVectorDataBinding parseXML(XmlObject document) {
		// try and parse as SOS and O&M V1
		if(!document.schemaType().isAssignableFrom(ObservationCollectionDocument.type)) {
			IllegalArgumentException e = new IllegalArgumentException("Expected o&m 1.0 ObservationCollection"); 
			LOGGER.error(e.getMessage());
			throw e;
		} else {
			// Convert to O&M 1.0 ObservationCollection XmlObject
			ObservationCollectionDocument observations = (ObservationCollectionDocument) 
					document.changeType(ObservationCollectionDocument.type);
			
			// Try and parse the document to a FeatureCollection
			GTVectorDataBinding parsedObservations;
			try {
				parsedObservations = parseObservations(observations);
			} catch (XmlException e) {
				IllegalArgumentException ex = new IllegalArgumentException("Error parseing SOS XML:", e); 
				LOGGER.error(ex.getMessage());
				throw ex;
			} catch (NoSuchAuthorityCodeException e) {
				IllegalArgumentException ex = new IllegalArgumentException("Error parseing observations:", e);
				LOGGER.error(ex.getMessage());
				throw ex;
			} catch (FactoryException e) {
				IllegalArgumentException ex = new IllegalArgumentException("Error parseing observations:", e);
				LOGGER.error(ex.getMessage());
				throw ex;
			}
			return parsedObservations;
		}
	}
	
	private GTVectorDataBinding parseObservations(ObservationCollectionDocument observationsDoc) throws XmlException, NoSuchAuthorityCodeException, FactoryException {
		// get the observations
		ObservationCollectionType observations = observationsDoc.getObservationCollection();
	
		int numMembers = observations.sizeOfMemberArray();
		LOGGER.debug("Parseing " + numMembers + "observations");
		
		// make a list to store the features
		List<SimpleFeature> simpleFeatureList = new ArrayList<SimpleFeature>();
		
		for(int i = 0; i < numMembers; i++) {
			ObservationType observation = observations.getMemberArray(i).getObservation();
			if(i == 0) {
				// create the feature type (schema) based on first observation
				type = createFeatureType(observation);
				featureBuilder = new SimpleFeatureBuilder(type);
			}
			// build the feature from the type and add it to the list
			SimpleFeature feature = convertToFeature(observation);
			simpleFeatureList.add(feature);
		}
		
		SimpleFeatureCollection collection = new ListFeatureCollection(type, simpleFeatureList);
		return new GTVectorDataBinding(collection);
	}
	
	/**
	 * Function to create a FeatureType from an observation
	 * 
	 * The FeatureType acts like a "schema" and goes with Features
	 * into the FeatureCollection
	 * 
	 * @param observation The observation to base the FeatureType on
	 * @return {@code SimpleFeatureType} The created FeatureType
	 * @throws XmlException If any critical problems are encountered during parsing, 
	 * e.g., due to missing elements. 
	 */
	private SimpleFeatureType createFeatureType(ObservationType observation) throws XmlException {
		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.setName("om-1.0-observation");
		
		// do required fields first, allows us to bail early in case of null fields
		builder.add(testNullReturnName(observation.getSamplingTime(), "samplingTime"), 
				TimeObjectPropertyType.class);
		builder.add(testNullReturnName(observation.getProcedure(), "procedure"),
				ProcessPropertyType.class);
		builder.add(testNullReturnName(observation.getObservedProperty(), "observedProperty"),
				PhenomenonPropertyType.class);
		builder.add(testNullReturnName(observation.getFeatureOfInterest(), "featureOfInterest"),
				FeaturePropertyType.class);
		builder.add(testNullReturnName(observation.getResult(), "result"),
				XmlObject.class);
		
		if(observation.isSetResultTime()) {
			builder.add("resultTime", TimeObjectPropertyType.class);
		}
		if(observation.isSetResultQuality()) {
			builder.add("resultQuality", AnyOrReferenceType.class);
		}
		if(observation.isSetBoundedBy()) {
			builder.add("boundedBy", BoundingShapeType.class);
		}
		if(observation.isSetDescription()) {
			builder.add("description", StringOrRefType.class);
		}
		if(observation.isSetMetadata()) {
			builder.add("metadata", AnyOrReferenceType.class);
		}
		if(observation.sizeOfParameterArray() > 0) {
			// TODO: Parameters!
			LOGGER.warn("Ignoring parameters in observation: unimplemented.");
		}
		
		// geolocate using the FOI - contains a samplingpoint for UCD-twitter
		FeaturePropertyType foi = observation.getFeatureOfInterest();
		
		// there are problems using the XmlBeans representations of SOS
		// Dig down through the elements... this is a horrible hack!
		
		try {
			if(foi.selectChildren(
					new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint", "sa"))[0].selectChildren(
							new QName("http://www.opengis.net/sampling/1.0", "position", "sa"))[0].selectChildren(
									new QName("http://www.opengis.net/gml", "Point", "gml"))[0].selectChildren(
											new QName("http://www.opengis.net/gml", "pos", "gml")).length > 0) {
				builder.add("geometry", Point.class);
				builder.setDefaultGeometry("geometry");
			}
			
		} catch(ArrayIndexOutOfBoundsException e) {
			LOGGER.warn("Cannot parse geometry from FOI");
		}
			
		return builder.buildFeatureType();
	}

	/**
	 * Function to convert an observation to a SimpleFeature to be stored in a FeatureCollection
	 * This function uses the featureBuilder class member to construct the feature according
	 * to the previously generated SimpleFeatureType
	 * 
	 * @param observation {@code ObservationType} The observation as represented by O&amp;M v1.0
	 * @return {@code SimpleFeature} a feature representing this observation
	 * @throws XmlException if any required elements were not found during parsing
	 * @throws FactoryException 
	 * @throws NoSuchAuthorityCodeException 
	 */
	private SimpleFeature convertToFeature(ObservationType observation) throws XmlException {
		featureBuilder.add(ifNullThrowParseException(observation.getSamplingTime(), "samplingTime"));
		featureBuilder.add(ifNullThrowParseException(observation.getProcedure(), "procedure"));
		featureBuilder.add(ifNullThrowParseException(observation.getObservedProperty(), "observedProperty"));
		featureBuilder.add(ifNullThrowParseException(observation.getFeatureOfInterest(), "featureOfInterest"));
		featureBuilder.add(ifNullThrowParseException(observation.getResult(), "result"));
		if(observation.isSetResultTime()) {
			featureBuilder.add(observation.getResultTime());
		}
		if(observation.isSetResultQuality()) {
			featureBuilder.add(observation.getResultQuality());
		}
		if(observation.isSetBoundedBy()) {
			featureBuilder.add(observation.getBoundedBy());
		}

		if(observation.isSetDescription()) {
			featureBuilder.add(observation.getDescription());
		}
		if(observation.isSetMetadata()) {
			featureBuilder.add(observation.getMetadata());
		}
		
		// try to introspect the foi to pull out sampling point to geolocate
		// for UCD-Twitter-SOS1.0 this contains an sa:SamplingPoint
		// see comments in createFeatureType()
		FeaturePropertyType foi = observation.getFeatureOfInterest();
		
		/*
		// using the XML Beans for the FOI does not let us drill to sa:SamplingPoint
		// neither does it want to allow the type change, we need to strip out the
		// featureOfInterest parent tags I think...
		XmlObject[] children = foi.selectChildren(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint", "sa"));
		// now re-parse the samplingPoint element
		XmlObject xmlo = XmlObject.Factory.parse(children[0].getDomNode());
		// and convert to SamplingPointDocument
		SamplingPointDocument spd = (SamplingPointDocument) xmlo.changeType(SamplingPointDocument.type);
		PointType point = spd.getSamplingPoint().getPosition().getPoint();
		// This all works, but the point returns NULL for all useful method calls!
		// Therefore abandoning it and going to do it the manual way!
		*/
		
		try {
			// Drill down through the XML Elements... there is probably a nicer way to do this!! 
			XmlObject saSamplingPoint = foi.selectChildren(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint", "sa"))[0];		
			XmlObject saPosition = saSamplingPoint.selectChildren(new QName("http://www.opengis.net/sampling/1.0", "position", "sa"))[0];		
			XmlObject gmlPoint = saPosition.selectChildren(new QName("http://www.opengis.net/gml", "Point", "gml"))[0];
			XmlObject gmlPosition = gmlPoint.selectChildren(new QName("http://www.opengis.net/gml", "pos", "gml"))[0];
			
			String srsName = ((SimpleValue)gmlPosition.selectAttribute(new QName("", "srsName"))).getStringValue();
			String[] coords = ((SimpleValue)gmlPosition).getStringValue().split(" ");
			
			CoordinateReferenceSystem crs = CRS.decode(srsName);
			GeometryBuilder b = new GeometryBuilder(crs);
			
			LOGGER.warn("Detected CRS: " + crs);
	
			Point location;
			double x, y, z;
			x = Double.valueOf(coords[0]);
			y = Double.valueOf(coords[1]);
			if(coords.length == 3) {
				// 3d point
				z = Double.valueOf(coords[2]);
				location = b.createPoint(x, y, z); 
			} else {
				location = b.createPoint(x, y);
			}
			
			featureBuilder.add(location);
			 
		} catch(ArrayIndexOutOfBoundsException err) {
			XmlException e = new XmlException("Could not geolocate FOI - Expected format: //sa:SamplingPoint/sa:Position/gml:Point/gml:pos[text()='x y (z)'] : " + err);
			LOGGER.error(e.getMessage());
			throw e; 
		} catch (NoSuchAuthorityCodeException err) {
			XmlException e = new XmlException("Did not recognise SRS code : " + err);
			LOGGER.error(e.getMessage());
			throw e;
		} catch (FactoryException err) {
			XmlException e = new XmlException("Could not create appropriate geometry factory : " + err);
			LOGGER.error(e.getMessage());
			throw e;
		}
		
		return featureBuilder.buildFeature(null);
	}
	
	/**
	 * Utility function to test if an object is null, and if it is throw an XmlException
	 * 
	 * @param toTest The object to test
	 * @param elementName What to call the element in the XmlException
	 * @return Object The same object is returned
	 * @throws XmlException if toTest == null
	 */
	protected Object ifNullThrowParseException(Object toTest, String elementName) throws XmlException {
		if(toTest == null) {
			XmlException e = new XmlException("Could not parse required element: " + elementName);
			LOGGER.error(e.getMessage());
			throw e;
		}
		return toTest;
	}
	
	/**
	 * Utility function to test if an object is null, and if it is throw an XmlException
	 * 
	 * @param toTest The object to test
	 * @param elementName What to call the element in the XmlException
	 * @return String the elementName as it was passed
	 * @throws XmlException if toTest == null
	 */
	protected String testNullReturnName(Object toTest, String elementName) throws XmlException {
		if(toTest == null) {
			XmlException e = new XmlException("Could not parse required element: " + elementName);
			LOGGER.error(e.getMessage());
			throw e;
		}
		return elementName;
	}
	
	public static void main(String[] args) {
		
	}
}
