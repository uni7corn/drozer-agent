package com.reversec.dz.service_connectors;

import java.lang.ref.WeakReference;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.reversec.dz.Agent;
import com.reversec.dz.services.ClientService;
import com.reversec.dz.services.ConnectorService;
import com.reversec.dz.services.ServerService;
import com.reversec.jsolar.api.connectors.Connector;
import com.reversec.jsolar.api.connectors.Endpoint;
import com.reversec.jsolar.logger.LogMessage;

public class IncomingReplyHandler extends Handler {
	
	private final WeakReference<Agent> agent;

	public IncomingReplyHandler(Agent agent) {
		 this.agent = new WeakReference<Agent>(agent);
	}

	@Override
	public void handleMessage(Message msg) {
		Agent agent = this.agent.get();
		Bundle data = msg.getData();

		switch(msg.what) {
		case ClientService.MSG_GET_DETAILED_ENDPOINT_STATUS:
			agent.getEndpointManager().get(data.getInt(Endpoint.ENDPOINT_ID)).setDetailedStatus(data);
			break;

		case ClientService.MSG_GET_ENDPOINTS_STATUS:
			for(Endpoint e : agent.getEndpointManager().all())
				if(data.containsKey("endpoint-" + e.getId()))
					e.setStatus(Endpoint.Status.values()[data.getInt("endpoint-" + e.getId())]);
			break;

		case ServerService.MSG_GET_DETAILED_SERVER_STATUS:
			agent.getServerParameters().setDetailedStatus(data);
			break;

		case ServerService.MSG_GET_SERVER_STATUS:
			Connector.Status newStatus = Connector.Status.values()[data.getInt("server")];
			// Sync the enabled flag so the toggle button reflects the actual server
			// state even when the server was started externally (e.g. via BootReceiver).
			agent.getServerParameters().enabled = (newStatus == Connector.Status.ONLINE
					|| newStatus == Connector.Status.ACTIVE
					|| newStatus == Connector.Status.CONNECTING);
			agent.getServerParameters().setStatus(newStatus);
			break;

		case ConnectorService.MSG_LOG_MESSAGE:
			LogMessage log_message = new LogMessage(data.getBundle(Connector.CONNECTOR_LOG_MESSAGE));
			if (data.containsKey(Endpoint.ENDPOINT_ID))
				agent.getEndpointManager().get(data.getInt(Endpoint.ENDPOINT_ID)).getLogger().log(log_message);
			else
				agent.getServerParameters().getLogger().log(log_message);
			break;

		default:
			super.handleMessage(msg);
		}
	}

}
