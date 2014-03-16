/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(suppressConstructorProperties=true)
@Data
public class ServerInfo implements Serializable {
	private static final long serialVersionUID = 6744847358282980437L;
	
	final private String baseURL;
	final private String userName, password;
	final boolean authPreemptive;
	
	private String errorMessage;
	
	private boolean calDAV, cardDAV;
	private List<ResourceInfo>
		addressBooks = new LinkedList<ResourceInfo>(),
		calendars  = new LinkedList<ResourceInfo>();
	
	public boolean hasEnabledCalendars() {
		for (ResourceInfo calendar : calendars)
			if (calendar.enabled)
				return true;
		return false;
	}
	
	
	@RequiredArgsConstructor(suppressConstructorProperties=true)
	@Data
	public static class ResourceInfo implements Serializable {
		private static final long serialVersionUID = -5516934508229552112L;
		
		enum Type {
			ADDRESS_BOOK,
			CALENDAR
		}
		
		boolean enabled = false;
		
		final Type type;
		final boolean readOnly;
		final String path, title, description, color;
		
		String timezone;
	}
}
