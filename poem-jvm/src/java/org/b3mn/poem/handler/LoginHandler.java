/***************************************
 * Copyright (c) 2008
 * Bjoern Wagner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
****************************************/

package org.b3mn.poem.handler;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.b3mn.poem.Identity;
import org.b3mn.poem.Subject;
import org.b3mn.poem.manager.UserManager;
import org.b3mn.poem.util.HandlerWithoutModelContext;
import org.openid4java.OpenIDException;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.InMemoryConsumerAssociationStore;
import org.openid4java.consumer.InMemoryNonceVerifier;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.ParameterList;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchRequest;
import org.openid4java.message.ax.FetchResponse;
import org.openid4java.message.sreg.SRegRequest;

@HandlerWithoutModelContext(uri="/login")
public class LoginHandler extends HandlerBase {
    
	/**
	* This servlet provides openID authentication.
	*
	* This servlet is based on the work of Sutra Zhou, who published it within the
	* openid4java project under the Apache License, version 2.0.
	*
	* @author Martin Czuchra, Sutra Zhou
	*/
	
	public static final String OPENID_SESSION_IDENTIFIER = "openid";
	public static final String REPOSITORY_REDIRECT = "/poem/repository2";

	private ConsumerManager manager;
	
	@Override
	public void init() {
        try {
			this.manager = new ConsumerManager();
	        manager.setAssociations(new InMemoryConsumerAssociationStore());
	        manager.setNonceVerifier(new InMemoryNonceVerifier(5000));
		} catch (ConsumerException e) {
			// TODO implement error handling
		}

	}
	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse res, Identity subject, Identity object) throws Exception {
    	doPost(req, res, subject, object);
	}
	
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res, Identity subject, Identity object) throws Exception {
        if ("true".equals(req.getParameter("is_return"))) {
            processReturn(req, res);
        } else {
     	   // Convert OpenID identifier to lower case in order to prevent creating 	   
     	   // different accounts for the same OpenID
            String identifier = req.getParameter("openid_identifier").toLowerCase();
            this.authRequest(identifier, req, res);
        }

	}

    private void processReturn(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	Subject user = this.verifyResponse(req, resp);
    	if (user == null) {
    		this.getServletContext().getRequestDispatcher("/index.jsp")
    		.forward(req, resp);
    	} else {

    		// authentication successful.

    		// store openid in session for future use by java dispatcher.
    		req.getSession().setAttribute(OPENID_SESSION_IDENTIFIER,
    				user.getOpenId());

    		req.setAttribute("identifier", user.getOpenId());
    		resp.sendRedirect(req.getContextPath() + REPOSITORY_REDIRECT);
    	}
    }

    // --- placing the authentication request ---
    @SuppressWarnings("unchecked")
    public String authRequest(String userSuppliedString,
            HttpServletRequest httpReq, HttpServletResponse httpResp)
            throws IOException, ServletException {
        try {
            // configure the return_to URL where your application will receive
            // the authentication responses from the OpenID provider
            // String returnToUrl = "http://example.com/openid";
            String returnToUrl = httpReq.getRequestURL().toString()
                    + "?is_return=true";

            // perform discovery on the user-supplied identifier
            List discoveries = manager.discover(userSuppliedString);

            // attempt to associate with the OpenID provider
            // and retrieve one service endpoint for authentication
            DiscoveryInformation discovered = manager.associate(discoveries);

            // store the discovery information in the user's session
            httpReq.getSession().setAttribute("openid-disc", discovered);

            // obtain a AuthRequest message to be sent to the OpenID provider
            AuthRequest authReq = manager.authenticate(discovered, returnToUrl);

            // Attribute Exchange example: fetching the 'email' attribute
            FetchRequest fetch = FetchRequest.createFetchRequest();
            SRegRequest sregReq = SRegRequest.createFetchRequest();

            fetch.addAttribute("nickname",
            		"http://schema.openid.net/contact/nickname", false);
            sregReq.addAttribute("nickname", false);
            fetch.addAttribute("email",
            		"http://schema.openid.net/contact/email", false);
            sregReq.addAttribute("email", false);
            fetch.addAttribute("fullname",
            		"http://schema.openid.net/contact/fullname", false);
            sregReq.addAttribute("fullname", false);
            fetch.addAttribute("dob",
            		"http://schema.openid.net/contact/dob", true);
            sregReq.addAttribute("dob", false);
            fetch.addAttribute("gender",
            		"http://schema.openid.net/contact/gender", false);
            sregReq.addAttribute("gender", false);
            fetch.addAttribute("postcode",
            		"http://schema.openid.net/contact/postcode", false);
            sregReq.addAttribute("postcode", false);
            fetch.addAttribute("country",
            		"http://schema.openid.net/contact/country", false);
            sregReq.addAttribute("country", false);
            fetch.addAttribute("language",
            		"http://schema.openid.net/contact/language", false);
            sregReq.addAttribute("language", false);

            authReq.addExtension(fetch);
            authReq.addExtension(sregReq);

            if (!discovered.isVersion2()) {
                // Option 1: GET HTTP-redirect to the OpenID Provider endpoint
                // The only method supported in OpenID 1.x
                // redirect-URL usually limited ~2048 bytes
                httpResp.sendRedirect(authReq.getDestinationUrl(true));
                return null;
            } else {
                // Option 2: HTML FORM Redirection (Allows payloads >2048 bytes)

         	   httpResp.getWriter().println(
         			   this.getRedirectPage(authReq, authReq.getDestinationUrl(true)));
            }
        } catch (OpenIDException e) {
            // present error to the user
        	OpenIDException test = e;
            throw new ServletException(e);
        }
        return null;
    }

    // --- processing the authentication response ---
    @SuppressWarnings("unchecked")
    public Subject verifyResponse(HttpServletRequest httpReq, HttpServletResponse httpResponse)
            throws ServletException {
        try {
            // extract the parameters from the authentication response
            // (which comes in as a HTTP request from the OpenID provider)
            ParameterList response = new ParameterList(httpReq
                    .getParameterMap());

            // retrieve the previously stored discovery information
            DiscoveryInformation discovered = (DiscoveryInformation) httpReq
                    .getSession().getAttribute("openid-disc");

            // extract the receiving URL from the HTTP request
            StringBuffer receivingURL = httpReq.getRequestURL();
            String queryString = httpReq.getQueryString();
            if (queryString != null && queryString.length() > 0)
                receivingURL.append("?").append(httpReq.getQueryString());

            // verify the response; ConsumerManager needs to be the same
            // (static) instance used to place the authentication request
            VerificationResult verification = manager.verify(receivingURL
                    .toString(), response, discovered);

            // examine the verification result and extract the verified
            // identifier
            Identifier verified = verification.getVerifiedId();
            if (verified != null) {
                AuthSuccess authSuccess = (AuthSuccess) verification
                        .getAuthResponse();

                UserManager um = UserManager.getInstance();
                
                // Check weather the response contains additional open id attributes
                if (authSuccess.hasExtension(AxMessage.OPENID_NS_AX)) {
                    FetchResponse fetchResp = (FetchResponse) authSuccess
                            .getExtension(AxMessage.OPENID_NS_AX);

                	// TODO: implement an update mechanism which doesn't overwrite existing
                	// values with null
                	/*
                	// Update database values
                	Subject user = um.login(verified.getIdentifier(), httpReq, httpResponse);	
                	user.setNickname((String)fetchResp.getAttributeValues("nickname").get(0));
					user.setFullname((String)fetchResp.getAttributeValues("fullname").get(0));
					user.setEmail((String)fetchResp.getAttributeValues("email").get(0));
					user.setDob((Date)fetchResp.getAttributeValues("dob").get(0));
					user.setGender((String)fetchResp.getAttributeValues("gender").get(0));
					user.setPostcode((String)fetchResp.getAttributeValues("postcode").get(0));
					user.setCountry((String)fetchResp.getAttributeValues("country").get(0));
					user.setLanguage((String)fetchResp.getAttributeValues("language").get(0));
					um.updateUser(user);
					*/
                    // if the user doesn't exists
                    if (!um.userExists(verified.getIdentifier())) {
						// Create new user with open id attributes
						um.CreateNewUser(verified.getIdentifier(), 
								(String)fetchResp.getAttributeValues("nickname").get(0), 
								(String)fetchResp.getAttributeValues("fullname").get(0),
								(String)fetchResp.getAttributeValues("email").get(0),
								null, // TODO parse date to java format
								(String)fetchResp.getAttributeValues("gender").get(0), 
								(String)fetchResp.getAttributeValues("postcode").get(0), 
								(String)fetchResp.getAttributeValues("country").get(0), 
								(String)fetchResp.getAttributeValues("language").get(0), 
								null, 
								null, 
								null, 
								null);
					}
                }
                // No additional attributes are passed
                if (!um.userExists(verified.getIdentifier())) {
                	// Create new user without open id attributes
                	um.CreateNewUser(verified.getIdentifier(), null, null, null, null, null, null, null, null, null, null, null, null);
                }
                return UserManager.getInstance().login(verified.getIdentifier(), httpReq, httpResponse);
            }
        } catch (OpenIDException e) {
            // present error to the user
            throw new ServletException(e);
        }

        return null;
    }
    
    // Returns a string containing a Html page with the necessary OpenID parameter encoded as Html form
    // This may look stupid but it's recommended in the OpenID standard. The page automatically redirects to the 
    // OpenID provider. 
    protected String getRedirectPage(AuthRequest authReq, String destinationUrl) {
 	   String page = 
 	   "<html xmlns=\"http://www.w3.org/1999/xhtml\">" +
 	   "<head>" +
 	       "<title>OpenID HTML FORM Redirection</title>" +
 	   "</head>" +
 	   "<body onload=\"document.forms['openid-form-redirection'].submit();\">" +
 	       "<form name=\"openid-form-redirection\" action=\""+destinationUrl+"\" method=\"post\" accept-charset=\"utf-8\" >";
        // Create hidden input field for each key value pair
 	   for (Object key : authReq.getParameterMap().keySet()) {
     	   String strKey = (String) key;
     	   page += "<input type=\"hidden\" name\"" + strKey + "\" value=\"" + authReq.getParameterValue(strKey) + "\"/>";
        }
 	   page += "<button type=\"submit\">Continue...</button>" +
 	       "</form>" +
 	   "</body>" +
 	   "</html>";

 	   
 	   return page;
    }
	
}
