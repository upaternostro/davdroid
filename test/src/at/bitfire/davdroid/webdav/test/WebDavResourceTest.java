package at.bitfire.davdroid.webdav.test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import at.bitfire.davdroid.webdav.HttpPropfind;
import at.bitfire.davdroid.webdav.InvalidDavResponseException;
import at.bitfire.davdroid.webdav.NotFoundException;
import at.bitfire.davdroid.webdav.PreconditionFailedException;
import at.bitfire.davdroid.webdav.WebDavResource;
import at.bitfire.davdroid.webdav.WebDavResource.MultigetType;
import at.bitfire.davdroid.webdav.WebDavResource.PutMode;

// tests require running robohydra!

public class WebDavResourceTest extends InstrumentationTestCase {
	static final String ROBOHYDRA_BASE = "http://10.0.0.11:3000/";
	static byte[] SAMPLE_CONTENT = new byte[] { 1, 2, 3, 4, 5 };
	
	AssetManager assetMgr;
	WebDavResource simpleFile,
		davCollection, davNonExistingFile, davExistingFile,
		davInvalid;

	@Override
	protected void setUp() throws Exception {
		assetMgr = getInstrumentation().getContext().getResources().getAssets();
		
		simpleFile = new WebDavResource(new URI(ROBOHYDRA_BASE + "assets/test.random"), false);
		
		davCollection = new WebDavResource(new URI(ROBOHYDRA_BASE + "dav"), true);
		davNonExistingFile = new WebDavResource(davCollection, "collection/new.file");
		davExistingFile = new WebDavResource(davCollection, "collection/existing.file");
		
		davInvalid = new WebDavResource(new URI(ROBOHYDRA_BASE + "dav-invalid"), true);
	}
	

	/* test resource name handling */
	
	public void testGetName() {
		// collection names should have a trailing slash
		assertEquals("dav", davCollection.getName());
		// but non-collection names shouldn't
		assertEquals("test.random", simpleFile.getName());
	}
	
	public void testTrailingSlash() throws URISyntaxException {
		// collections should have a trailing slash
		assertEquals("/dav/", davCollection.getLocation().getPath());
		// but non-collection members shouldn't
		assertEquals("/assets/test.random", simpleFile.getLocation().getPath());
	}
	
	
	/* test feature detection */
	
	public void testOptions() throws URISyntaxException, IOException, HttpException {
		String[]	davMethods = new String[] { "PROPFIND", "GET", "PUT", "DELETE" },
					davCapabilities = new String[] { "addressbook", "calendar-access" };
		
		// server without DAV
		simpleFile.options();
		for (String method : davMethods)
			assertFalse(simpleFile.supportsMethod(method));
		for (String capability : davCapabilities)
			assertFalse(simpleFile.supportsDAV(capability));
		
		// server with DAV
		davCollection.options();
		for (String davMethod : davMethods)
			assert(davCollection.supportsMethod(davMethod));
		for (String capability : davCapabilities)
			assert(davCollection.supportsDAV(capability));
	}

	public void testPropfindCurrentUserPrincipal() throws IOException, HttpException {
		davCollection.propfind(HttpPropfind.Mode.CURRENT_USER_PRINCIPAL);
		assertEquals("/dav/principals/users/test", davCollection.getCurrentUserPrincipal());
		
		try {
			simpleFile.propfind(HttpPropfind.Mode.CURRENT_USER_PRINCIPAL);
			fail();
		} catch(InvalidDavResponseException ex) {
		}
		assertNull(simpleFile.getCurrentUserPrincipal());
	}
		
	public void testPropfindHomeSets() throws IOException, HttpException {
		WebDavResource dav = new WebDavResource(davCollection, "principals/users/test");
		dav.propfind(HttpPropfind.Mode.HOME_SETS);
		assertEquals("/dav/addressbooks/test", dav.getAddressbookHomeSet());
		assertEquals("/dav/calendars/test/", dav.getCalendarHomeSet());
	}
	
	public void testPropfindAddressBooks() throws IOException, HttpException {
		WebDavResource dav = new WebDavResource(davCollection, "addressbooks/test", true);
		dav.propfind(HttpPropfind.Mode.MEMBERS_COLLECTIONS);
		assertEquals(2, dav.getMembers().size());
		for (WebDavResource member : dav.getMembers()) {
			if (member.getName().equals("default.vcf"))
				assertTrue(member.isAddressBook());
			else
				assertFalse(member.isAddressBook());
			assertFalse(member.isCalendar());
		}
	}
	
	public void testPropfindCalendars() throws IOException, HttpException {
		WebDavResource dav = new WebDavResource(davCollection, "calendars/test", true);
		dav.propfind(HttpPropfind.Mode.MEMBERS_COLLECTIONS);
		assertEquals(3, dav.getMembers().size());
		assertEquals("0xFF00FF", dav.getMembers().get(2).getColor());
		for (WebDavResource member : dav.getMembers()) {
			if (member.getName().contains(".ics"))
				assertTrue(member.isCalendar());
			else
				assertFalse(member.isCalendar());
			assertFalse(member.isAddressBook());
		}
	}
	
	
	/* test normal HTTP/WebDAV */
	
	public void testDontFollowRedirections() throws URISyntaxException, IOException {
		WebDavResource redirection = new WebDavResource(new URI(ROBOHYDRA_BASE + "redirect"), false);
		try {
			redirection.get();
			fail();
		} catch (HttpException e) {
		}
	}
	
	public void testGet() throws URISyntaxException, IOException, HttpException {
		simpleFile.get();
		assertTrue(IOUtils.contentEquals(
			assetMgr.open("test.random", AssetManager.ACCESS_STREAMING),
			simpleFile.getContent()
		));
	}
	
	public void testMultiGet() throws InvalidDavResponseException, IOException, HttpException {
		WebDavResource davAddressBook = new WebDavResource(davCollection, "addressbooks/default.vcf", true);
		davAddressBook.multiGet(new String[] { "1.vcf", "2.vcf" }, MultigetType.ADDRESS_BOOK);
		assertEquals(2, davAddressBook.getMembers().size());
		for (WebDavResource member : davAddressBook.getMembers()) {
			assertNotNull(member.getContent());
		}
	}
	
	public void testPutAddDontOverwrite() throws IOException, HttpException {
		// should succeed on a non-existing file
		davNonExistingFile.put(SAMPLE_CONTENT, PutMode.ADD_DONT_OVERWRITE);
		
		// should fail on an existing file
		try {
			davExistingFile.put(SAMPLE_CONTENT, PutMode.ADD_DONT_OVERWRITE);
			fail();
		} catch(PreconditionFailedException ex) {
		}
	}
	
	public void testPutUpdateDontOverwrite() throws IOException, HttpException {
		// should succeed on an existing file
		davExistingFile.put(SAMPLE_CONTENT, PutMode.UPDATE_DONT_OVERWRITE);
		
		// should fail on a non-existing file
		try {
			davNonExistingFile.put(SAMPLE_CONTENT, PutMode.UPDATE_DONT_OVERWRITE);
			fail();
		} catch(PreconditionFailedException ex) {
		}
	}
	
	public void testDelete() throws IOException, HttpException {
		// should succeed on an existing file
		davExistingFile.delete();
		
		// should fail on a non-existing file
		try {
			davNonExistingFile.delete();
			fail();
		} catch (NotFoundException e) {
		}
	}
	
	
	/* test CalDAV/CardDAV */
	
	
	/* special test */
	
	public void testInvalidURLs() throws IOException, HttpException {
		WebDavResource dav = new WebDavResource(davInvalid, "addressbooks/user%40domain/");
		dav.propfind(HttpPropfind.Mode.MEMBERS_COLLECTIONS);
		List<WebDavResource> members = dav.getMembers();
		assertEquals(2, members.size());
		assertEquals(ROBOHYDRA_BASE + "dav/addressbooks/user%40domain/My%20Contacts%3A1.vcf/", members.get(0).getLocation().toString());
		assertEquals("HTTPS://example.com/user%40domain/absolute-url.vcf", members.get(1).getLocation().toString());
	}
	
}
