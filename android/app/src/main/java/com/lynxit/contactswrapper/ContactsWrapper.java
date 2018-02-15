package com.lynxit.contactswrapper;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.util.Log;

import java.net.URI;
import java.util.*;

import com.facebook.react.*;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.uimanager.ViewManager;

public class ContactsWrapper extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static final int CONTACT_REQUEST = 1;
    private static final int EMAIL_REQUEST = 2;
    public static final String E_CONTACT_CANCELLED = "E_CONTACT_CANCELLED";
    public static final String E_CONTACT_NO_DATA = "E_CONTACT_NO_DATA";
    public static final String E_CONTACT_NO_EMAIL = "E_CONTACT_NO_EMAIL";
    public static final String E_CONTACT_EXCEPTION = "E_CONTACT_EXCEPTION";
    public static final String E_CONTACT_PERMISSION = "E_CONTACT_PERMISSION";
    private Promise mContactsPromise;
    private Activity mCtx;
    private final ContentResolver contentResolver;
    private static final List<String> JUST_ME_PROJECTION = new ArrayList<String>() {{
        add(ContactsContract.Contacts.Data.MIMETYPE);
        add(ContactsContract.Profile.DISPLAY_NAME);
        add(ContactsContract.CommonDataKinds.Contactables.PHOTO_URI);
        add(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
        add(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
        add(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
        add(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
        add(ContactsContract.CommonDataKinds.Phone.NUMBER);
        add(ContactsContract.CommonDataKinds.Phone.TYPE);
        add(ContactsContract.CommonDataKinds.Phone.LABEL);
        add(ContactsContract.CommonDataKinds.Email.DATA);
        add(ContactsContract.CommonDataKinds.Email.ADDRESS);
        add(ContactsContract.CommonDataKinds.Email.TYPE);
        add(ContactsContract.CommonDataKinds.Email.LABEL);
    }};


    public ContactsWrapper(ReactApplicationContext reactContext) {
        super(reactContext);
        this.contentResolver = getReactApplicationContext().getContentResolver();
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "ContactsWrapper";
    }



    @ReactMethod
    public void getContact(Promise contactsPromise) {
        launchPicker(contactsPromise, CONTACT_REQUEST);
    }

    @ReactMethod
    public void getEmail(Promise contactsPromise) {
        launchPicker(contactsPromise, EMAIL_REQUEST);
    }

    /**
     * Lanch the contact picker, with the specified requestCode for returned data.
     * @param contactsPromise - promise passed in from React Native.
     * @param requestCode - request code to specify what contact data to return
     */
    private void launchPicker(Promise contactsPromise, int requestCode) {
//        this.contentResolver.query(Uri.parse("content://com.android.contacts/contacts/lookup/0r3-A7416BA07AEA92F2/3"), null, null, null, null);
        Cursor cursor = this.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (cursor != null) {
            mContactsPromise = contactsPromise;
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
            mCtx = getCurrentActivity();
            if (intent.resolveActivity(mCtx.getPackageManager()) != null) {
                mCtx.startActivityForResult(intent, requestCode);
            }
            cursor.close();
        }else{
            mContactsPromise.reject(E_CONTACT_PERMISSION, "no permission");
        }
    }

    @Override
    public void onActivityResult(Activity ContactsWrapper, final int requestCode, final int resultCode, final Intent intent) {

        if(mContactsPromise == null || mCtx == null
              || (requestCode != CONTACT_REQUEST && requestCode != EMAIL_REQUEST)){
          return;
        }

        String email = null;
        switch (resultCode) {
            case (Activity.RESULT_OK):
                Uri contactUri = intent.getData();
                switch(requestCode) {
                    case(CONTACT_REQUEST):
                        try {
                            /* Retrieve all possible data about contact and return as a JS object */

                            //First get ID
                            String id = null;
                            int idx;
                            WritableMap contactData = Arguments.createMap();
                            Cursor cursor = this.contentResolver.query(contactUri, null, null, null, null);
                            if (cursor != null && cursor.moveToFirst()) {
                                idx = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                                id = cursor.getString(idx);
                            } else {
                                mContactsPromise.reject(E_CONTACT_NO_DATA, "Contact Data Not Found");
                                return;
                            }


                            // Build the Entity URI.
                            Uri.Builder b = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id).buildUpon();
                            b.appendPath(ContactsContract.Contacts.Entity.CONTENT_DIRECTORY);
                            contactUri = b.build();

                            // Create the projection (SQL fields) and sort order.
                            String[] projection = {
                                ContactsContract.Contacts.Entity.MIMETYPE,
                                StructuredName.FAMILY_NAME,
                                StructuredName.GIVEN_NAME,
                                StructuredName.MIDDLE_NAME,
                                Email.ADDRESS,
                            };
                            String sortOrder = ContactsContract.Contacts.Entity.RAW_CONTACT_ID + " ASC";
                            cursor = this.contentResolver.query(contactUri, projection, null, null, sortOrder);
                            if(cursor == null)  return;

                            String mime;
                            boolean foundData = false;

                            WritableArray allEmails = new WritableNativeArray();

                            int mimeIdx = cursor.getColumnIndex(ContactsContract.Contacts.Entity.MIMETYPE);
                            if (cursor.moveToFirst()) {
                                do {
                                    mime = cursor.getString(mimeIdx);
                                    if (mime.equals(StructuredName.CONTENT_ITEM_TYPE)) {

                                        String name = cursor.getString(cursor.getColumnIndex(StructuredName.DISPLAY_NAME));
                                        String familyName = cursor.getString(cursor.getColumnIndex(StructuredName.FAMILY_NAME));
                                        String givenName = cursor.getString(cursor.getColumnIndex(StructuredName.GIVEN_NAME));
                                        String middleName = cursor.getString(cursor.getColumnIndex(StructuredName.MIDDLE_NAME));

                                        if (name != null) {
                                            contactData.putString("name", name);
                                            foundData = true;
                                        }
                                        if (familyName != null) {
                                            contactData.putString("familyName", familyName);
                                            foundData = true;
                                        }
                                        if (givenName != null) {
                                            contactData.putString("givenName", givenName);
                                            foundData = true;
                                        }
                                        if (middleName != null) {
                                            contactData.putString("middleName", middleName);
                                            foundData = true;
                                        }

                                    } else if(mime.equals(Email.CONTENT_ITEM_TYPE)) {

                                        String currentEmail = cursor.getString(cursor.getColumnIndex(Email.ADDRESS));
                                        allEmails.pushString(currentEmail);
                                        if (!contactData.hasKey("email")) {
                                            contactData.putString("email", currentEmail);
                                        }

                                        foundData = true;

                                    }
                                } while (cursor.moveToNext());
                            }

                            contactData.putArray("emailAddresses", allEmails);

                            cursor.close();
                            if(foundData) {
                                mContactsPromise.resolve(contactData);
                                return;
                            } else {
                                mContactsPromise.reject(E_CONTACT_NO_DATA, "No data found for contact");
                                return;
                            }
                        } catch (Exception e) {
                            mContactsPromise.reject(E_CONTACT_EXCEPTION, e.getMessage());
                            return;
                        }
                        /* No need to break as all paths return */
                    case(EMAIL_REQUEST):
                        /* Return contacts first email address, as string */
                        try {


                            // get the contact id from the Uri
                            String id = contactUri.getLastPathSegment();

                            // query for everything email
                            Cursor cursor = mCtx.getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                                                            null, ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?", new String[]{id},
                                                                            null);

                            int emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);

                            // For now, return only the first email address, as a string
                            if (cursor.moveToFirst()) {
                                email = cursor.getString(emailIdx);
                                mContactsPromise.resolve(email);
                                return;
                            } else {
                                //Contact has no email address stored
                                mContactsPromise.reject(E_CONTACT_NO_EMAIL, "No email found for contact");
                                return;
                            }
                        } catch (Exception e) {
                            mContactsPromise.reject(E_CONTACT_EXCEPTION, e.getMessage());
                            return;
                        }
                        /* No need to break as all paths return */
                    default:
                        //Unexpected return code - shouldn't happen, but catch just in case
                        mContactsPromise.reject(E_CONTACT_EXCEPTION, "Unexpected error in request");
                        return;
                }
            default:
                //Request was cancelled
                mContactsPromise.reject(E_CONTACT_CANCELLED, "Cancelled");
                return;
        }
    }

    public void onNewIntent(Intent intent) {

    }
}
