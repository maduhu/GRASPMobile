package it.fabaris.wfp.task;

import it.fabaris.wfp.activities.FormListCompletedActivity;
import it.fabaris.wfp.activities.PreferencesActivity;
import it.fabaris.wfp.activities.R;
import it.fabaris.wfp.listener.MyCallback;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

/**
 * Class that defines the task that send the xform to the server
 * 
 */

public class HttpSendPostTask extends AsyncTask<String, Void, String> {
	ProgressDialog pd;
	String http;
	String phone;
	String data; //la form da inviare
	boolean isSendAllForms;//LL 17-04-2014 per gestire la visualizzazione del toast nel caso di invio di piu' form insieme
	Context context;
	MyCallback callback;
	private Lock condCheck;

	public HttpSendPostTask(Context context, String http, String phone,
			String data, MyCallback callback, Lock cond, boolean isSendAllForms) {
		this.context = context;
		this.http = http;
		this.phone = phone;
		this.data = data; //la form da inviare
		this.callback = callback;
		this.condCheck = cond;
		this.isSendAllForms = isSendAllForms;
		
	}

	@Override
	protected void onPreExecute() {
		
		pd = ProgressDialog.show(context,
				context.getString(R.string.checking_server),
				context.getString(R.string.wait));
	
		
	}

	@Override
	protected String doInBackground(String... params) {
		String result = "";
		if (!isOnline()) {
			result = "offline";
		} else if (PreferencesActivity.SERVER_ONLINE == "NO") {
			result = "server error";
		} else {
			result = postCall(http, phone, data);
		}
		return result;
	}

	@Override
	protected void onPostExecute(String result) 
	{
		Log.i("httpSendPostTaskOnPostEx", result);
		if(pd.isShowing() && result != null)
		{	
			pd.dismiss();

			if (result.equalsIgnoreCase("Offline"))
			{
				FormListCompletedActivity.updateFormToFinalized();
				Toast.makeText(context, R.string.device_not_online,	Toast.LENGTH_SHORT).show();
				if (callback != null)
				{
					callback.callbackCall();
				}
			}
			else if (result.trim().equalsIgnoreCase("server error")) 
			{
				FormListCompletedActivity.updateFormToFinalized();
				Toast.makeText(context, R.string.check_connection, Toast.LENGTH_SHORT).show();
				if (callback != null)
				{
					callback.callbackCall();
				}
			}
			else if (result.trim().equalsIgnoreCase("formnotonserver"))
			{
				FormListCompletedActivity.updateFormToFinalized();
				Toast.makeText(context, R.string.form_not_available, Toast.LENGTH_SHORT).show();
			}
			else if (result.trim().equalsIgnoreCase("Error")) 
			{
				Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show();
			}
			else if (result.trim().toLowerCase().startsWith("ok")) 
			{
				Log.i("RESULT", "messaggio ricevuto dal server");
	
				//--------------------------------------------------------------------------
				XPathFactory factory = XPathFactory.newInstance();
				XPath xPath = factory.newXPath();
				XPathExpression xPathExpression;
				DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
				try 
				{
					DocumentBuilder builder = builderFactory.newDocumentBuilder();
					ByteArrayInputStream bin = new ByteArrayInputStream(result
							.substring(result.indexOf("-") + 1).getBytes());
					Document xmlDocument = builder.parse(bin);
					bin.close();
					xPathExpression = xPath.compile("/response/formResponseID");
					String id = xPathExpression.evaluate(xmlDocument);

					FormListCompletedActivity.setID(id);
					
				//------------------------------------------------------------------------
				} 
				catch (XPathExpressionException e) 
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ParserConfigurationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SAXException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				/**
				 * AGGIORNA LO STATO DELLA FORM
				 */
				FormListCompletedActivity.updateFormToSubmitted();
				if(!isSendAllForms)//se non si stanno inviando piu' form insieme
					Toast.makeText(context, R.string.forms_sent, Toast.LENGTH_LONG).show();
				
				if (callback != null) 
				{
					callback.callbackCall();
					synchronized (condCheck) 
					{
						condCheck.notify();
					}
				}
			}
		}
	}
	

	private String postCall(String url, String phone, String data) {
		/**
		 *  set parameter
		 */
		String result = null;
		HttpPost httpPost = new HttpPost(url);
		HttpParams httpParameters = new BasicHttpParams();
		// HttpConnectionParams.setConnectionTimeout(httpParameters, 10000);
		// HttpConnectionParams.setSoTimeout(httpParameters, 10000);
		DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
		List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
		nameValuePair.add(new BasicNameValuePair("phoneNumber", phone));
		nameValuePair.add(new BasicNameValuePair("data", data));
		// Url Encoding the POST parameters
		try {
			httpPost.setEntity(new UrlEncodedFormEntity(nameValuePair));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return result = "error";
		}
		/**
		 *  Making HTTP Request
		 */
		try {
			HttpResponse response = httpClient.execute(httpPost);
			result = EntityUtils.toString(response.getEntity());

			if (result.equalsIgnoreCase("\r\n")) {
				return result = "formnotonserver";
			} else {
				return result = "ok-" + result;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return result = "error";
		}
	}

	private boolean isOnline() {
		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		if (ni == null) {
			return false;
		}
		return ni.isConnected();
	}
}