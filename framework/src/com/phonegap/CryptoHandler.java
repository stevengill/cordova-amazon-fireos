package com.phonegap;

import android.webkit.WebView;

public class CryptoHandler extends Module {
	
	WebView mView;
	
	public CryptoHandler(WebView view, DroidGap gap)
	{
		super(view, gap);
		mView = view;
	}
	
	public void encrypt(String pass, String text)
	{
		try {
			String encrypted = SimpleCrypto.encrypt(pass,text);
			mView.loadUrl("javascript:Crypto.gotCryptedString('" + text + "')");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void decrypt(String pass, String text)
	{
		try {
			String decrypted = SimpleCrypto.decrypt(pass,text);
			mView.loadUrl("javascript:Crypto.gotPlainString('" + text + "')");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
