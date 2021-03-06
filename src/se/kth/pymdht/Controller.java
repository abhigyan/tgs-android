package se.kth.pymdht;

import android.util.Log;

import java.io.BufferedReader;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import se.kth.pymdht.IncomingMsg.MsgError;

public class Controller {
	
	public class LookupDone extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private static final int MAX_EMPTY_HEARTBEATS = 5;
	private Id _my_id;
	private SwiftTracker swift_tracker = new SwiftTracker();
	private GetPeersLookup lookup;
	private OverlayBootstrapper bootstrapper;
	private int empty_heartbeats_in_a_row;
	
	public Controller(BufferedReader unstable, BufferedReader stable){
		this._my_id = new RandomId();
		this.bootstrapper = new OverlayBootstrapper(unstable, stable);
		this.lookup = null;
		empty_heartbeats_in_a_row = 0;
	}
	
	public void start(){
		
	}
	
	public List<DatagramPacket> on_heartbeat() throws LookupDone{
		List<DatagramPacket> datagrams_to_send;
		if (this.lookup == null){
			datagrams_to_send = new ArrayList<DatagramPacket>(0);
		}
		else{
			datagrams_to_send = lookup.get_datagrams();
			if (datagrams_to_send.size() == 0){
				this.empty_heartbeats_in_a_row += 1;
				System.out.println(this.empty_heartbeats_in_a_row);
				if (this.empty_heartbeats_in_a_row > MAX_EMPTY_HEARTBEATS){
					throw new LookupDone();
				}
			}
			else{
				this.empty_heartbeats_in_a_row = 0;
			}
		}
		System.out.println(System.currentTimeMillis() + " heartbeat sends " + datagrams_to_send.size());
		return datagrams_to_send;
	}
	
	public List<DatagramPacket> on_datagram_received(DatagramPacket datagram){
		this.empty_heartbeats_in_a_row = 0;
		List<DatagramPacket> datagrams_to_send = new ArrayList<DatagramPacket>();
		IncomingMsg msg = null;
		try{
			msg = new IncomingMsg(datagram);
		}catch (MsgError e){
			// this is not a DHT message.
		}
		if (msg == null){
			// this is not a DHT message. Swift?
			//if swift: create lookup
			if (swift_tracker.on_handshake(datagram)){
				Log.w("pymdht.controller", "got HANDSHAKE from Swift");
				datagrams_to_send.add(swift_tracker.handshake_reply_datagram);
				lookup = new GetPeersLookup(swift_tracker.hash, bootstrapper);
				datagrams_to_send = lookup.get_datagrams();
				datagrams_to_send.add(swift_tracker.handshake_reply_datagram);
			}
			else{
				//ignore
			}
		}	
		else{
			//DHT get_peers response
			lookup.on_response(msg);
			//more lookup queries
			datagrams_to_send = lookup.get_datagrams();
			//peers
			List<ByteBuffer> cpeers = lookup.get_cpeers();
			if (cpeers.size() > 0){
				Log.w("pymdht.controller", System.currentTimeMillis() + " peers " + cpeers.size());
				DatagramPacket pex_datagram = swift_tracker.get_swift_pex_datagram(cpeers);
				datagrams_to_send.add(pex_datagram);
			}
			
		}
		System.out.println(System.currentTimeMillis() + " on_datagram sends " + datagrams_to_send.size());
		return datagrams_to_send;
	}
}
