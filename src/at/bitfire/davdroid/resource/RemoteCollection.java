/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import lombok.Getter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.ValidationException;

import org.apache.http.HttpException;

import ezvcard.VCardException;
import android.util.Log;
import at.bitfire.davdroid.webdav.HttpPropfind;
import at.bitfire.davdroid.webdav.InvalidDavResponseException;
import at.bitfire.davdroid.webdav.WebDavResource;
import at.bitfire.davdroid.webdav.WebDavResource.MultigetType;
import at.bitfire.davdroid.webdav.WebDavResource.PutMode;

public abstract class RemoteCollection<T extends Resource> {
	private static final String TAG = "davdroid.RemoteCollection";
	
	@Getter WebDavResource collection;

	abstract protected String memberContentType();
	abstract protected MultigetType multiGetType();
	abstract protected T newResourceSkeleton(String name, String ETag);
	
	public RemoteCollection(String baseURL, String user, String password, boolean preemptiveAuth) throws URISyntaxException {
		collection = new WebDavResource(new URI(baseURL), user, password, preemptiveAuth, true);
	}

	
	/* collection operations */

	public String getCTag() throws IOException, HttpException {
		try {
			if (collection.getCTag() == null && collection.getMembers() == null)	// not already fetched
				collection.propfind(HttpPropfind.Mode.COLLECTION_CTAG);
		} catch (InvalidDavResponseException e) {
			return null;
		}
		return collection.getCTag();
	}
	
	public Resource[] getMemberETags() throws IOException, InvalidDavResponseException, HttpException {
		collection.propfind(HttpPropfind.Mode.MEMBERS_ETAG);
			
		List<T> resources = new LinkedList<T>();
		if (collection.getMembers() != null) {
			for (WebDavResource member : collection.getMembers())
				resources.add(newResourceSkeleton(member.getName(), member.getETag()));
			return resources.toArray(new Resource[0]);
		} else
			return null;
	}
	
	@SuppressWarnings("unchecked")
	public Resource[] multiGet(Resource[] resources) throws IOException, InvalidDavResponseException, HttpException {
		try {
			if (resources.length == 1) {
				Resource resource = get(resources[0]);
				return (resource != null) ? (T[]) new Resource[] { resource } : null;
			}
			
			LinkedList<String> names = new LinkedList<String>();
			for (Resource resource : resources)
				names.add(resource.getName());
			
			collection.multiGet(names.toArray(new String[0]), multiGetType());
			
			LinkedList<T> foundResources = new LinkedList<T>();
			if (collection.getMembers() != null)
				for (WebDavResource member : collection.getMembers()) {
					T resource = newResourceSkeleton(member.getName(), member.getETag());
					try {
						InputStream is = member.getContent();
						if (is != null) {
							resource.parseEntity(is);
							foundResources.add(resource);
						} else
							Log.e(TAG, "Ignoring entity without content");
					} catch (ParserException ex) {
						Log.e(TAG, "Ignoring unparseable iCal in multi-response", ex);
					} catch (VCardException ex) {
						Log.e(TAG, "Ignoring unparseable vCard in multi-response", ex);
					}
				}
			else
				return null;
			
			return foundResources.toArray(new Resource[0]);
		} catch (ParserException ex) {
			Log.e(TAG, "Couldn't parse iCal from GET", ex);
		} catch (VCardException ex) {
			Log.e(TAG, "Couldn't parse vCard from GET", ex);
		}
		
		return new Resource[0];
	}
	
	
	/* internal member operations */

	public Resource get(Resource resources) throws IOException, HttpException, ParserException, VCardException {
		WebDavResource member = new WebDavResource(collection, resources.getName());
		member.get();
		resources.parseEntity(member.getContent());
		return resources;
	}
	
	public void add(Resource res) throws IOException, HttpException, ValidationException {
		WebDavResource member = new WebDavResource(collection, res.getName(), res.getETag());
		member.setContentType(memberContentType());
		member.put(res.toEntity().getBytes("UTF-8"), PutMode.ADD_DONT_OVERWRITE);
		
		collection.invalidateCTag();
	}

	public void delete(Resource res) throws IOException, HttpException {
		WebDavResource member = new WebDavResource(collection, res.getName(), res.getETag());
		member.delete();
		
		collection.invalidateCTag();
	}
	
	public void update(Resource res) throws IOException, HttpException, ValidationException {
		WebDavResource member = new WebDavResource(collection, res.getName(), res.getETag());
		member.setContentType(memberContentType());
		member.put(res.toEntity().getBytes("UTF-8"), PutMode.UPDATE_DONT_OVERWRITE);
		
		collection.invalidateCTag();
	}
}
