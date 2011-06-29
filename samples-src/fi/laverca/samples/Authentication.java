package fi.laverca.samples;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.Set;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.util.encoders.Base64;
import org.etsi.uri.TS102204.v1_1_2.Service;

import fi.ficom.mss.TS102204.v1_0_0.Status;
import fi.laverca.DTBS;
import fi.laverca.FiComAdditionalServices;
import fi.laverca.FiComAdditionalServices.PersonIdAttribute;
import fi.laverca.FiComClient;
import fi.laverca.FiComException;
import fi.laverca.FiComRequest;
import fi.laverca.FiComResponse;
import fi.laverca.FiComResponseHandler;
import fi.laverca.JvmSsl;
import fi.laverca.ProgressUpdate;

/**
 * Sample for demonstrating authentication. 
 * 
 * @author Jan Mikael Lindl�f (mikael.lindlof@nbl.fi)
 * @author Eemeli Miettinen (eemeli.miettinen@methics.fi)
 */
public class Authentication {
	
	private static final String CONFIG_LOCATION = "fi/laverca/samples/configuration.xml";
	private static final Log log = LogFactory.getLog(Authentication.class);
	
	/**
     * Creates a response window.
     */
    private class ResponseWindow {
    	
    	private javax.swing.JProgressBar callStateProgressBar;
    	private javax.swing.JButton cancelButton;
    	private String eventId;
        private javax.swing.JScrollPane jScrollPane1;
        private FiComRequest req;
        private javax.swing.JTextArea responseBox;
        private javax.swing.JFrame responseFrame;
    	
        /**
         * Generates a new window for responses and calls <code>estamblishConnection</code> 
         * to start the authentication process.
         * @param number
         */
    	public ResponseWindow(String number) {
    		Long currentTimeMillis = System.currentTimeMillis();
    		eventId = "A" + currentTimeMillis.toString().substring(currentTimeMillis.toString().length()-4);
    		initResponse();
    		connect(number);
    	}
    	
    	/**
    	 * Creates a new FiComClient using an xml configuration file
    	 * @return
    	 */    	
    	public FiComClient createFiComClient(){
    		XMLConfiguration config = null;
    		try {
    		    config = new XMLConfiguration(CONFIG_LOCATION);
    		} catch(ConfigurationException e) {
    		    log.info("configuration file not found", e);
    		}
    		
    		log.info("setting up ssl");
    		JvmSsl.setSSL(config.getString("ssl.trustStore"),
    				config.getString("ssl.trustStorePassword"),
    				config.getString("ssl.keyStore"),
    				config.getString("ssl.keyStorePassword"),
    				config.getString("ssl.keyStoreType"));
    		
    		String apId  = config.getString("ap.apId");
            String apPwd = config.getString("ap.apPwd");

            String msspSignatureUrl    = config.getString("mssp.msspSignatureUrl");
            String msspStatusUrl       = config.getString("mssp.msspStatusUrl");
            String msspReceiptUrl      = config.getString("mssp.msspReceiptUrl");

            log.info("creating FiComClient");
            FiComClient fiComClient = new FiComClient(apId, 
                                                      apPwd, 
                                                      msspSignatureUrl, 
                                                      msspStatusUrl, 
                                                      msspReceiptUrl);
            return fiComClient;
    	}
    	/**
    	 * 
    	 * @return
    	 */
    	private LinkedList<Service> createAdditionalServices(){

            LinkedList<Service> additionalServices = new LinkedList<Service>();
            LinkedList<String> attributeNames = new LinkedList<String>(); 
     
            attributeNames.add(FiComAdditionalServices.PERSON_ID_VALIDUNTIL);
            attributeNames.add(FiComAdditionalServices.PERSON_ID_ADDRESS);
            attributeNames.add(FiComAdditionalServices.PERSON_ID_AGE);
            attributeNames.add(FiComAdditionalServices.PERSON_ID_SUBJECT);
            attributeNames.add(FiComAdditionalServices.PERSON_ID_SURNAME);
            attributeNames.add(FiComAdditionalServices.PERSON_ID_GIVENNAME);
            attributeNames.add(FiComAdditionalServices.PERSON_ID_HETU);
            attributeNames.add(FiComAdditionalServices.PERSON_ID_SATU);
            
            Service personIdService = FiComAdditionalServices.createPersonIdService(attributeNames);
            Service validateService = FiComAdditionalServices.createValidateService();
            additionalServices.add(personIdService);
            additionalServices.add(validateService);

            return additionalServices;
    	}
    	
    	
    	/**
    	 * Creates services, connects to MSSP using SSL and waits for response.
    	 * @param phoneNumber
    	 */
    	private void connect(final String phoneNumber) {
    		
            FiComClient fiComClient = createFiComClient();
            Long currentTimeMillis = System.currentTimeMillis();
            String apTransId = "A"+currentTimeMillis;
            
            Service eventIdService = FiComAdditionalServices.createEventIdService(eventId);
            Service noSpamService = FiComAdditionalServices.createNoSpamService("A12", false);
            LinkedList<Service> additionalServices = createAdditionalServices();
            
            byte[] authnChallenge = new DTBS(apTransId, DTBS.ENCODING_UTF8).toBytes();
            
            try {
                log.info("calling authenticate");
                req = 
    	            fiComClient.authenticate(apTransId, 
    	            		authnChallenge, 
    	            		phoneNumber, 
    	            		noSpamService, 
    	            		eventIdService,
    	            		additionalServices, 
    	            		new FiComResponseHandler() {  
    	            	
								@Override
				            	public void onResponse(FiComRequest req, FiComResponse resp) {
				            		log.info("got resp");
				            		printResponse(resp, eventId);
    								callStateProgressBar.setIndeterminate(false);
				            	}
							
    			            	@Override
    			            	public void onError(FiComRequest req, Throwable throwable) {
    			            		log.info("got error", throwable);
    			            		responseBox.setText(responseBox.getText() + throwable);
    								callStateProgressBar.setIndeterminate(false);
    			            	}
    			            	
    			            	@Override
    							public void onOutstandingProgress(FiComRequest req, ProgressUpdate prgUpdate) {
    								log.info("got progress update");
    							}
    			            });
                
            }
            catch (IOException e) {
                log.info("error establishing connection", e);
            }

            fiComClient.shutdown();
    	}
    	
    	/**
    	 * Analyzes the FiComRequest and prints the results to responseBox
    	 * @param resp
    	 * @param eventId
    	 */
       	private void printResponse(FiComResponse resp, String eventId){
    		String response = "testi";
    		Status validationStatus = resp.getAeValidationStatus();

    		try {
    			response = "MSS Signature: " + 
    			new String(Base64.encode(resp.getMSS_StatusResp().
    			getMSS_Signature().getBase64Signature()), "ASCII") +
    			"\n\n";
    			response+="Pkcs7" + "\n";
    			response+="   Serial: " + resp.getPkcs7Signature().getSignerCert().getSerialNumber();
    			response+="   Type: " + resp.getPkcs7Signature().getSignerCert().getType();
    			response+="   SigAlgName: " + resp.getPkcs7Signature().getSignerCert().getSigAlgName();
    			response+="   SerialNumber: " + resp.getPkcs7Signature().getSignerCert().getSerialNumber();
    			response+="   SigAlgOID: " + resp.getPkcs7Signature().getSignerCert().getSigAlgOID() + "\n";
    			response+="   IssuerX500Principal: " + resp.getPkcs7Signature().getSignerCert().getIssuerX500Principal() + "\n";
    			response+="   SubjectX500Principal: " + resp.getPkcs7Signature().getSignerCert().getSubjectX500Principal() + "\n";
    			response+="\n";
    			response+="AP   id: " + resp.getMSS_StatusResp().getAP_Info().getAP_ID();
    			response+="   PWD: " + resp.getMSS_StatusResp().getAP_Info().getAP_PWD();
    			response+="   TransID: " + resp.getMSS_StatusResp().getAP_Info().getAP_TransID() + "\n";
    			response+="MobileUser   MSISDN: " + resp.getMSS_StatusResp().getMobileUser().getMSISDN();
    			response+="\n";
    		    response+="CriticalExtensionOIDs:\n";
    		    response+=resp.getPkcs7Signature().getSignerCert().getIssuerX500Principal() + "\n";
    		    response+="AE validation status code: " + validationStatus.getStatusCode().getValue();
        		response+=" (" + validationStatus.getStatusMessage() + ")\n";

    			Set<String> oids = resp.getPkcs7Signature().getSignerCert().getNonCriticalExtensionOIDs();
    			response +="Event ID: " + eventId + "\n";	
    			for (String oid : oids) {
    				response +="   " + oid + "\n";
    			}
    			response +="NonCriticalExtensionOIDs:" + "\n";
    			oids = resp.getPkcs7Signature().getSignerCert().getCriticalExtensionOIDs();
    			for (String oid : oids) {
    				response +="   " + oid + "\n";
    			}
    			
        		for(PersonIdAttribute a : resp.getPersonIdAttributes()) {
        			response +=a.getName().substring(a.getName().indexOf('#')+1) + ": " + a.getStringValue() 
        					+ "\n";
        		}
        		
    		} catch (FiComException e1) {
    			log.info(e1);
    		} catch (UnsupportedEncodingException e) {
    			log.info("Unsupported encoding", e);
    		} catch (NullPointerException e){
    			log.info(e);
    		}
    		responseBox.setText(response);
    	}
    	
    	
    	/**
    	 * Initializes the response box
    	 */
    	private void initResponse() {
        	responseFrame = new javax.swing.JFrame(eventId);
            cancelButton = new javax.swing.JButton();
            jScrollPane1 = new javax.swing.JScrollPane();
            responseBox = new javax.swing.JTextArea();
            callStateProgressBar = new javax.swing.JProgressBar();

            cancelButton.setText("Cancel");
            cancelButton.addActionListener(new ActionListener() {
    			public void actionPerformed(ActionEvent e) {
    				req.cancel();
    				callStateProgressBar.setIndeterminate(false);
    				responseFrame.dispose();
    			}
    		});
            
            responseBox.setColumns(20);
            responseBox.setRows(5);
            jScrollPane1.setViewportView(responseBox);
            callStateProgressBar.setIndeterminate(true);
            javax.swing.GroupLayout layout = new javax.swing.GroupLayout(responseFrame.getContentPane());
            responseFrame.getContentPane().setLayout(layout);
            layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addGap(18, 18, 18)
                    .addComponent(callStateProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 252, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(18, 18, 18)
                    .addComponent(cancelButton)
                    .addContainerGap(47, Short.MAX_VALUE))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(13, 13, 13)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 373, Short.MAX_VALUE)
                        .addGap(14, 14, 14)))
            );
            layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(callStateProgressBar, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE)
                        .addComponent(cancelButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGap(266, 266, 266))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addContainerGap(53, Short.MAX_VALUE)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 226, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(21, 21, 21)))
            );

            responseFrame.pack();
            responseFrame.setVisible(true);
            responseFrame.setResizable(false);
        }
    	
    	
    }
	private static javax.swing.JFrame frame;
    private static javax.swing.JLabel lblNumber;
	private static javax.swing.JTextField number;  
    private static javax.swing.JPanel pane;
    private static javax.swing.JButton sendButton;
    public static void main(String[] args) {
		new Authentication().initUI();
	}
    /**
	 * Initializes the UI
	 */
    private void initUI() {
    	frame = new javax.swing.JFrame("Authentication");
        pane = new javax.swing.JPanel();
        lblNumber = new javax.swing.JLabel();
        number = new javax.swing.JTextField();
        sendButton = new javax.swing.JButton();


        frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
        frame.setResizable(false);

        lblNumber.setText("Phone number");

        number.setText("+35847001001");

        sendButton.setText("Send");
        sendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new ResponseWindow(number.getText());
			}
		});
		

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(pane);
        pane.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lblNumber)
                    .addComponent(sendButton)
                    .addComponent(number, javax.swing.GroupLayout.DEFAULT_SIZE, 248, Short.MAX_VALUE))
                .addContainerGap(25, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(lblNumber)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(number, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sendButton)
                .addContainerGap(43, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(frame.getContentPane());
        frame.getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        frame.pack();
    }
    
}

