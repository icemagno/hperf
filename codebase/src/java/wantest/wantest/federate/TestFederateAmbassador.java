/*
 *   Copyright 2014 Calytrix Technologies
 *
 *   This file is part of wantest.
 *
 *   NOTICE:  All information contained herein is, and remains
 *            the property of Calytrix Technologies Pty Ltd.
 *            The intellectual and technical concepts contained
 *            herein are proprietary to Calytrix Technologies Pty Ltd.
 *            Dissemination of this information or reproduction of
 *            this material is strictly forbidden unless prior written
 *            permission is obtained from Calytrix Technologies Pty Ltd.
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 */
package wantest.federate;

import org.apache.log4j.Logger;

import hla.rti1516e.AttributeHandleValueMap;
import hla.rti1516e.NullFederateAmbassador;
import hla.rti1516e.ObjectClassHandle;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.OrderType;
import hla.rti1516e.TransportationTypeHandle;
import hla.rti1516e.exceptions.FederateInternalError;

public class TestFederateAmbassador extends NullFederateAmbassador
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------

	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private Logger logger;
	private Storage storage;

	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	public TestFederateAmbassador( Storage storage )
	{
		this.logger = Logger.getLogger( "wantest" );
		this.storage = storage;
	}

	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------

	public void discoverObjectInstance( ObjectInstanceHandle theObject,
	                                    ObjectClassHandle theObjectClass,
	                                    String objectName )
		throws FederateInternalError
	{
		if( theObjectClass.equals(Handles.CLASS_TEST_FEDERATE) )
		{
			//
			// Class: HLAobjectRoot.TestFederate
			//
			storage.peers.put( objectName, new TestFederate(objectName) );
		}
		else if( theObjectClass.equals(Handles.CLASS_TEST_OBJECT) )
		{
			//
			// Class: HLAobjectRoot.TestObject
			//
			TestObject testObject = new TestObject( theObject, objectName );
			
			// attach an event record to both the global time list and the
			// object-specific list inside each object
			Event event = new Event( Event.Type.Discovery, theObject, 0, testObject.getCreateTime() );
			storage.eventlist.add( event );
			testObject.addEvent( event );
			storage.objects.put( theObject, testObject );
		}
		
		logger.debug( "   discoverObjectInstance(): class="+theObjectClass+
		              ", handle="+theObject+
		              ", name="+objectName );
	}

	
	public void reflectAttributeValues( ObjectInstanceHandle theObject,
	                                    AttributeHandleValueMap theAttributes,
	                                    byte[] userSuppliedTag,
	                                    OrderType sentOrdering,
	                                    TransportationTypeHandle theTransport,
	                                    SupplementalReflectInfo reflectInfo )
	    throws FederateInternalError
	{
		// find the object this is in reference to
		TestObject testObject = storage.objects.get( theObject );
		if( testObject == null )
			return; // not something we want to bother with
		
		// deserialize the attributes
		long receivedTimestamp = System.currentTimeMillis();
		byte[] bytes = theAttributes.getValueReference(Handles.ATT_LAST_UPDATED).array();
		long sentTimestamp = Long.parseLong( new String(bytes) );

		// only record the event if the object is valid
		// before then we send an initial update, and we don't want to count that
		if( testObject.isValid() )
		{
			// create an event and link it into the master list and test object's list
			Event event = new Event( Event.Type.Reflection, theObject, sentTimestamp, receivedTimestamp );
			storage.eventlist.add( event );
			testObject.addEvent( event );
		}
		else
		{
			// this is the first update, get what we need
			String creator = new String( theAttributes.getValueReference(Handles.ATT_CREATOR_NAME).array() );
			TestFederate federate = storage.peers.get( creator );
			if( federate == null )
				logger.error( "Received an update from an unknown federate (apparently)" );
			else
				testObject.setCreator( federate );
		}
	}
	
	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
