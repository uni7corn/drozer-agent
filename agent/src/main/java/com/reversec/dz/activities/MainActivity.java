package com.reversec.dz.activities;

import com.reversec.dz.Agent;
import com.reversec.dz.BuildConfig;
import com.reversec.dz.EndpointAdapter;
import com.reversec.dz.R;
import com.reversec.dz.views.EndpointListView;
import com.reversec.dz.views.ServerListRowView;
import com.reversec.jsolar.api.connectors.Endpoint;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

import com.reversec.dz.util.PentestPasswordManager;

public class MainActivity extends Activity {

	private static final int REQUEST_CODE_PENTEST_PERMISSIONS = 42;

	private EndpointListView endpoint_list_view = null;
	private ServerListRowView server_list_row_view = null;
	
	private void launchEndpointActivity(Endpoint endpoint) {
		Intent intent = new Intent(MainActivity.this, EndpointActivity.class);
		intent.putExtra(Endpoint.ENDPOINT_ID, endpoint.getId());
		
		MainActivity.this.startActivity(intent);
	}
	
	private void launchServerActivity() {
		MainActivity.this.startActivity(new Intent(MainActivity.this, ServerActivity.class));
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Agent.getInstance().setContext(this.getApplicationContext());
        PentestPasswordManager.ensureSecurityDefaults(this.getApplicationContext());

        setContentView(R.layout.activity_main);
        
        this.endpoint_list_view = (EndpointListView)this.findViewById(R.id.endpoint_list_view);
        this.endpoint_list_view.setAdapter(new EndpointAdapter(this.getApplicationContext(), Agent.getInstance().getEndpointManager(),
        		new EndpointAdapter.OnEndpointSelectListener() {

		        	@Override
					public void onEndpointSelect(Endpoint endpoint) {
						MainActivity.this.launchEndpointActivity(endpoint);
					}
		
					@Override
					public void onEndpointToggle(Endpoint endpoint, boolean isChecked) {
						if(isChecked)
							MainActivity.this.startEndpoint(endpoint);
						else
							MainActivity.this.stopEndpoint(endpoint);
					}
					
		}));
        
        this.server_list_row_view = (ServerListRowView)this.findViewById(R.id.server_list_row_view);
        this.server_list_row_view.setServerParameters(Agent.getInstance().getServerParameters());
        this.server_list_row_view.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				MainActivity.this.launchServerActivity();
			}
        	
        });
        this.server_list_row_view.setServerViewListener(new ServerListRowView.OnServerViewListener() {

			@Override
			public void onToggle(boolean toggle) {
				if(toggle)
					MainActivity.this.startServer();
				else
					MainActivity.this.stopServer();
			}

		});

        requestPentestPermissionsIfNeeded();
    }

    /**
     * Dynamically requests all dangerous permissions declared in the manifest that have not yet
     * been granted. Adding a new dangerous permission to the pentest manifest is sufficient —
     * no Java change is required. SYSTEM_ALERT_WINDOW is handled separately because it cannot
     * go through requestPermissions() and requires a Settings page redirect instead.
     * This method is a no-op for the store flavor (BuildConfig.IS_PENTEST == false).
     */
    private void requestPentestPermissionsIfNeeded() {
        if (!BuildConfig.IS_PENTEST) return;

        List<String> toRequest = new ArrayList<>();
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions != null) {
                for (String perm : info.requestedPermissions) {
                    try {
                        PermissionInfo pi = getPackageManager().getPermissionInfo(perm, 0);
                        int baseProtection = pi.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                        if (baseProtection == PermissionInfo.PROTECTION_DANGEROUS
                                && ContextCompat.checkSelfPermission(this, perm)
                                   != PackageManager.PERMISSION_GRANTED) {
                            toRequest.add(perm);
                        }
                    } catch (PackageManager.NameNotFoundException ignored) {
                        // Unknown or system-internal permission — skip
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Should never happen for our own package
        }

        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                toRequest.toArray(new String[0]),
                REQUEST_CODE_PENTEST_PERMISSIONS);
        }

        // SYSTEM_ALERT_WINDOW grants background activity launch exemption on Android 10+.
        // It cannot go through requestPermissions() — redirect to system Settings.
        // canDrawOverlays() requires API 23+; pre-M grants overlay at install time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // No action needed; components operate regardless of individual grant outcomes
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    	case R.id.menu_refresh:
    		this.updateEndpointStatuses();
    		this.updateServerStatus();
    		return true;
    	
    	case R.id.menu_settings:
    		this.startActivity(new Intent(this, SettingsActivity.class));
    		return true;
    	
    	default:
    		return super.onOptionsItemSelected(item);
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	
    	Agent.getInstance().unbindServices();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	Agent.getInstance().bindServices();
    }
    
    private void startServer(){
    	try {
			Agent.getInstance().getServerService().startServer(Agent.getInstance().getServerParameters(), Agent.getInstance().getMessenger());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    private void stopServer(){
    	try {
			Agent.getInstance().getServerService().stopServer(Agent.getInstance().getServerParameters(), Agent.getInstance().getMessenger());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private void startEndpoint(Endpoint endpoint){
    	try {
    		Agent.getInstance().getClientService().startEndpoint(endpoint, Agent.getInstance().getMessenger());
			
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    private void stopEndpoint(Endpoint endpoint){
    	try {
    		Agent.getInstance().getClientService().stopEndpoint(endpoint, Agent.getInstance().getMessenger());
			
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    protected void updateEndpointStatuses() {
    	try {
			for(Endpoint e : Agent.getInstance().getEndpointManager().all())
				e.setStatus(Endpoint.Status.UPDATING);
			
			Agent.getInstance().getClientService().getEndpointStatuses(Agent.getInstance().getMessenger());
		}
		catch(RemoteException e) {
			for(Endpoint e2 : Agent.getInstance().getEndpointManager().all())
				e2.setStatus(Endpoint.Status.UNKNOWN);
			
			Toast.makeText(this, R.string.service_offline, Toast.LENGTH_SHORT).show();
		}
    }
    
    protected void updateServerStatus() {
		try {
			Agent.getInstance().getServerParameters().setStatus(com.reversec.jsolar.api.connectors.Server.Status.UPDATING);
			
			Agent.getInstance().getServerService().getServerStatus(Agent.getInstance().getMessenger());
		}
		catch (RemoteException e) {
			Agent.getInstance().getServerParameters().setStatus(Endpoint.Status.UNKNOWN);
			
			Toast.makeText(this, R.string.service_offline, Toast.LENGTH_SHORT).show();
		}
    }
    
}
