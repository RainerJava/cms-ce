/*
 * Copyright 2000-2013 Enonic AS
 * http://www.enonic.com/license
 */

package com.enonic.cms.web.portal.services;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.joda.time.DateMidnight;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import com.enonic.esl.containers.ExtendedMap;
import com.enonic.esl.util.DateUtil;

import com.enonic.cms.api.client.model.user.Gender;
import com.enonic.cms.api.plugin.ext.userstore.UserStoreConfigField;
import com.enonic.cms.core.Attribute;
import com.enonic.cms.core.portal.httpservices.HttpServicesException;
import com.enonic.cms.core.security.PortalSecurityHolder;
import com.enonic.cms.core.security.SecurityService;
import com.enonic.cms.core.security.user.StoreNewUserCommand;
import com.enonic.cms.core.security.user.UserKey;
import com.enonic.cms.core.security.user.UserType;
import com.enonic.cms.core.security.userstore.StoreNewUserStoreCommand;
import com.enonic.cms.core.security.userstore.UserStoreKey;
import com.enonic.cms.core.security.userstore.UserStoreService;
import com.enonic.cms.api.plugin.ext.userstore.UserStoreConfig;
import com.enonic.cms.core.servlet.ServletRequestAccessor;
import com.enonic.cms.core.structure.SiteKey;
import com.enonic.cms.core.structure.SitePath;
import com.enonic.cms.api.plugin.ext.userstore.UserFieldType;
import com.enonic.cms.api.plugin.ext.userstore.UserFields;
import com.enonic.cms.itest.AbstractSpringTest;
import com.enonic.cms.itest.util.DomainFixture;
import com.enonic.cms.store.dao.UserDao;
import com.enonic.cms.web.portal.SiteRedirectHelper;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.easymock.classextension.EasyMock.createMock;

public class UserServicesProcessorTest_operation_UpdateTest
    extends AbstractSpringTest
{

    @Autowired
    private UserDao userDao;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private UserStoreService userStoreService;

    @Autowired
    private DomainFixture fixture;

    private MockHttpServletRequest request = new MockHttpServletRequest();

    private MockHttpServletResponse response = new MockHttpServletResponse();

    private MockHttpSession session = new MockHttpSession();

    private UserServicesProcessor userHandlerController;

    @Before
    public void setUp()
    {
        fixture.initSystemData();

        userHandlerController = new UserServicesProcessor();
        userHandlerController.setUserDao( userDao );
        userHandlerController.setSecurityService( securityService );
        userHandlerController.setUserStoreService( userStoreService );
        userHandlerController.setUserServicesRedirectHelper( new UserServicesRedirectUrlResolver() );

        // just need a dummy of the SiteRedirectHelper
        userHandlerController.setSiteRedirectHelper( createMock( SiteRedirectHelper.class ) );

        request.setRemoteAddr( "127.0.0.1" );
        ServletRequestAccessor.setRequest( request );

        PortalSecurityHolder.setAnonUser( fixture.findUserByName( "anonymous" ).getKey() );

    }

    @After
    public void after()
    {
        securityService.logoutPortalUser();
    }

    @Test
    public void update_given_fields_not_sent_then_fields_must_not_be_nulled()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.HOME_PAGE, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.ORGANIZATION, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LOCALE, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.GENDER, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.HTML_EMAIL, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.BIRTHDAY, "" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setFirstName( "First name" );
        userFields.setLastName( "Last name" );
        userFields.setInitials( "INI" );
        userFields.setBirthday( new DateMidnight( 2012, 12, 12 ).toDate() );
        userFields.setOrganization( "Java Mafia" );
        userFields.setHomePage( "http://www.homepage.com" );
        userFields.setLocale( Locale.FRENCH );
        userFields.setGender( Gender.FEMALE );
        userFields.setHtmlEmail( true );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // setup: verify user
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertNotNull( userFields.getBirthday() );
        assertEquals( new DateMidnight( 2012, 12, 12 ).toDate(), userFields.getBirthday() );
        assertEquals( "INI", userFields.getInitials() );
        assertEquals( new DateMidnight( 2012, 12, 12 ).toDate(), userFields.getBirthday() );
        assertEquals( "http://www.homepage.com", userFields.getHomePage() );
        assertEquals( "Java Mafia", userFields.getOrganization() );
        assertEquals( Locale.FRENCH, userFields.getLocale() );
        assertEquals( Gender.FEMALE, userFields.getGender() );
        assertEquals( Boolean.TRUE, userFields.getHtmlEmail() );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "email", "testuser@example.com" );
        formItems.putString( "first_name", "First name changed" );
        formItems.putString( "last_name", "Last name changed" );
        formItems.putString( "initials", "Initials changed" );

        loginPortalUser( "testuser" );

        userHandlerController.handlerUpdate( request, response, session, formItems, null, null );

        // verify
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( "Initials changed", userFields.getInitials() );
        assertEquals( "Last name changed", userFields.getLastName() );
        assertEquals( "First name changed", userFields.getFirstName() );
        assertEquals( null, userFields.getHomePage() );
        assertEquals( null, userFields.getBirthday() );
        assertEquals( null, userFields.getHomePage() );
        assertEquals( null, userFields.getOrganization() );
        assertEquals( null, userFields.getLocale() );
        assertEquals( null, userFields.getGender() );
        assertEquals( null, userFields.getHtmlEmail() );
    }

    @Test
    public void update_given_user_field_with_empty_value()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.HOME_PAGE, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.ORGANIZATION, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LOCALE, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.GENDER, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.HTML_EMAIL, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.BIRTHDAY, "" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userInfo = new UserFields();
        userInfo.setFirstName( "First name" );
        userInfo.setLastName( "Last name" );
        userInfo.setInitials( "INI" );
        userInfo.setBirthday( new DateMidnight( 2012, 12, 12 ).toDate() );
        userInfo.setOrganization( "Java Mafia" );
        userInfo.setHomePage( "http://www.homepage.com" );
        userInfo.setLocale( Locale.FRENCH );
        userInfo.setGender( Gender.FEMALE );
        userInfo.setHtmlEmail( true );
        createNormalUser( "testuser", "myLocalStore", userInfo );

        // setup: verify user
        userInfo = fixture.findUserByName( "testuser" ).getUserFields();
        assertNotNull( userInfo.getBirthday() );
        assertEquals( new DateMidnight( 2012, 12, 12 ).toDate(), userInfo.getBirthday() );
        assertEquals( "INI", userInfo.getInitials() );
        assertEquals( new DateMidnight( 2012, 12, 12 ).toDate(), userInfo.getBirthday() );
        assertEquals( "http://www.homepage.com", userInfo.getHomePage() );
        assertEquals( "Java Mafia", userInfo.getOrganization() );
        assertEquals( Locale.FRENCH, userInfo.getLocale() );
        assertEquals( Gender.FEMALE, userInfo.getGender() );
        assertEquals( Boolean.TRUE, userInfo.getHtmlEmail() );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "email", "testuser@example.com" );
        formItems.putString( "first_name", "First name changed" );
        formItems.putString( "last_name", "Last name changed" );
        formItems.putString( "initials", "Initials changed" );
        formItems.putString( "home_page", "" );
        formItems.putString( "organization", "" );
        formItems.putString( "locale", "" );
        formItems.putString( "gender", "" );
        formItems.putString( "html-email", "" );
        formItems.putString( "birthday", "" );

        loginPortalUser( "testuser" );

        userHandlerController.handlerUpdate( request, response, session, formItems, null, null );

        // verify
        userInfo = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( "Initials changed", userInfo.getInitials() );
        assertEquals( "", userInfo.getOrganization() );
        assertEquals( "", userInfo.getHomePage() );
        assertEquals( null, userInfo.getLocale() );
        assertEquals( null, userInfo.getGender() );
        assertEquals( null, userInfo.getHtmlEmail() );
        assertEquals( null, userInfo.getBirthday() );
    }

    @Test
    public void update_given_non_textual_fields_not_sent_then_fields_must_set_to_null()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.BIRTHDAY, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.GENDER, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.HTML_EMAIL, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LOCALE, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.TIME_ZONE, "" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setBirthday( new DateMidnight( 2012, 12, 12 ).toDate() );
        userFields.setGender( Gender.FEMALE );
        userFields.setHtmlEmail( true );
        userFields.setInitials( "INI" );
        userFields.setLocale( Locale.FRENCH );
        userFields.setTimezone( TimeZone.getTimeZone( "UTC" ) );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // setup: verify user
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertNotNull( userFields.getBirthday() );
        assertEquals( "INI", userFields.getInitials() );
        assertEquals( new DateMidnight( 2012, 12, 12 ).toDate(), userFields.getBirthday() );
        assertEquals( Gender.FEMALE, userFields.getGender() );
        assertEquals( Boolean.TRUE, userFields.getHtmlEmail() );
        assertEquals( Locale.FRENCH, userFields.getLocale() );
        assertEquals( TimeZone.getTimeZone( "UTC" ), userFields.getTimeZone() );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "email", "testuser@example.com" );
        formItems.putString( "initials", "Initials changed" );

        loginPortalUser( "testuser" );

        userHandlerController.handlerUpdate( request, response, session, formItems, null, null );

        // verify
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( "Initials changed", userFields.getInitials() );
        assertEquals( null, userFields.getLocale() );
        assertEquals( null, userFields.getGender() );
        assertEquals( null, userFields.getHtmlEmail() );
        assertEquals( null, userFields.getBirthday() );
        assertEquals( null, userFields.getTimeZone() );
    }

    @Test
    public void update_given_non_textual_fields_with_value_empty_then_fields_must_set_to_null()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.BIRTHDAY, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.GENDER, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.HTML_EMAIL, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LOCALE, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.TIME_ZONE, "" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setBirthday( new DateMidnight( 2012, 12, 12 ).toDate() );
        userFields.setGender( Gender.FEMALE );
        userFields.setHtmlEmail( true );
        userFields.setInitials( "INI" );
        userFields.setLocale( Locale.FRENCH );
        userFields.setTimezone( TimeZone.getTimeZone( "UTC" ) );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // setup: verify user
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertNotNull( userFields.getBirthday() );
        assertEquals( "INI", userFields.getInitials() );
        assertEquals( new DateMidnight( 2012, 12, 12 ).toDate(), userFields.getBirthday() );
        assertEquals( Gender.FEMALE, userFields.getGender() );
        assertEquals( Boolean.TRUE, userFields.getHtmlEmail() );
        assertEquals( Locale.FRENCH, userFields.getLocale() );
        assertEquals( TimeZone.getTimeZone( "UTC" ), userFields.getTimeZone() );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "email", "testuser@example.com" );
        formItems.putString( "initials", "Initials changed" );
        formItems.putString( "locale", "" );
        formItems.putString( "gender", "" );
        formItems.putString( "html-email", "" );
        formItems.putString( "birthday", "" );
        formItems.putString( "time-zone", "" );

        loginPortalUser( "testuser" );

        userHandlerController.handlerUpdate( request, response, session, formItems, null, null );

        // verify
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( "Initials changed", userFields.getInitials() );
        assertEquals( null, userFields.getLocale() );
        assertEquals( null, userFields.getGender() );
        assertEquals( null, userFields.getHtmlEmail() );
        assertEquals( null, userFields.getBirthday() );
        assertEquals( null, userFields.getTimeZone() );
    }

    @Test
    public void update_given_non_textual_fields_with_value_null_then_fields_must_set_to_null()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.BIRTHDAY, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.GENDER, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.HTML_EMAIL, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LOCALE, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.TIME_ZONE, "" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setBirthday( new DateMidnight( 2012, 12, 12 ).toDate() );
        userFields.setGender( Gender.FEMALE );
        userFields.setHtmlEmail( true );
        userFields.setInitials( "INI" );
        userFields.setLocale( Locale.FRENCH );
        userFields.setTimezone( TimeZone.getTimeZone( "UTC" ) );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // setup: verify user
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertNotNull( userFields.getBirthday() );
        assertEquals( "INI", userFields.getInitials() );
        assertEquals( new DateMidnight( 2012, 12, 12 ).toDate(), userFields.getBirthday() );
        assertEquals( Gender.FEMALE, userFields.getGender() );
        assertEquals( Boolean.TRUE, userFields.getHtmlEmail() );
        assertEquals( Locale.FRENCH, userFields.getLocale() );
        assertEquals( TimeZone.getTimeZone( "UTC" ), userFields.getTimeZone() );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "email", "testuser@example.com" );
        formItems.putString( "initials", "Initials changed" );
        formItems.putString( "locale", null );
        formItems.putString( "gender", null );
        formItems.putString( "html-email", null );
        formItems.putString( "birthday", null );
        formItems.putString( "time-zone", null );

        loginPortalUser( "testuser" );

        userHandlerController.handlerUpdate( request, response, session, formItems, null, null );

        // verify
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( "Initials changed", userFields.getInitials() );
        assertEquals( null, userFields.getLocale() );
        assertEquals( null, userFields.getGender() );
        assertEquals( null, userFields.getHtmlEmail() );
        assertEquals( null, userFields.getBirthday() );
        assertEquals( null, userFields.getTimeZone() );
    }

    @Test
    public void update_given_textual_user_field_with_empty_value_then_value_is_set_to_empty()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.ORGANIZATION, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LOCALE, "" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setFirstName( "First name" );
        userFields.setLastName( "Last name" );
        userFields.setInitials( "INI" );
        userFields.setOrganization( "Org" );
        userFields.setLocale( Locale.GERMAN );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // setup: verify user
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( "First name", userFields.getFirstName() );
        assertEquals( "Last name", userFields.getLastName() );
        assertEquals( "INI", userFields.getInitials() );
        assertEquals( "Org", userFields.getOrganization() );
        assertEquals( Locale.GERMAN, userFields.getLocale() );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "email", "testuser@example.com" );
        formItems.putString( "locale", Locale.FRENCH.toString() );
        formItems.putString( "first-name", "" );
        formItems.putString( "last-name", "" );
        formItems.putString( "initials", "" );
        formItems.putString( "organization", "" );

        loginPortalUser( "testuser" );

        userHandlerController.handlerUpdate( request, response, session, formItems, null, null );

        // verify
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( Locale.FRENCH, userFields.getLocale() );
        assertEquals( "", userFields.getFirstName() );
        assertEquals( "", userFields.getLastName() );
        assertEquals( "", userFields.getInitials() );
        assertEquals( "", userFields.getOrganization() );
    }

    @Test
    public void update_given_textual_user_field_with_value_null_then_value_is_set_to_null()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.ORGANIZATION, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LOCALE, "" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setFirstName( "First name" );
        userFields.setLastName( "Last name" );
        userFields.setInitials( "INI" );
        userFields.setOrganization( "Org" );
        userFields.setLocale( Locale.GERMAN );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // setup: verify user
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( "First name", userFields.getFirstName() );
        assertEquals( "Last name", userFields.getLastName() );
        assertEquals( "INI", userFields.getInitials() );
        assertEquals( "Org", userFields.getOrganization() );
        assertEquals( Locale.GERMAN, userFields.getLocale() );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "email", "testuser@example.com" );
        formItems.putString( "locale", Locale.FRENCH.toString() );
        formItems.putString( "first-name", null );
        formItems.putString( "last-name", null );
        formItems.putString( "initials", null );
        formItems.putString( "organization", null );

        loginPortalUser( "testuser" );

        userHandlerController.handlerUpdate( request, response, session, formItems, null, null );

        // verify
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( Locale.FRENCH, userFields.getLocale() );
        assertEquals( null, userFields.getFirstName() );
        assertEquals( null, userFields.getLastName() );
        assertEquals( null, userFields.getInitials() );
        assertEquals( null, userFields.getOrganization() );
    }

    @Test
    public void update_given_textual_user_field_not_sent_then_value_is_set_to_null()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.ORGANIZATION, "" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LOCALE, "" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setFirstName( "First name" );
        userFields.setLastName( "Last name" );
        userFields.setInitials( "INI" );
        userFields.setOrganization( "Org" );
        userFields.setLocale( Locale.GERMAN );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // setup: verify user
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( "First name", userFields.getFirstName() );
        assertEquals( "Last name", userFields.getLastName() );
        assertEquals( "INI", userFields.getInitials() );
        assertEquals( "Org", userFields.getOrganization() );
        assertEquals( Locale.GERMAN, userFields.getLocale() );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "email", "testuser@example.com" );
        formItems.putString( "locale", Locale.FRENCH.toString() );

        loginPortalUser( "testuser" );

        userHandlerController.handlerUpdate( request, response, session, formItems, null, null );

        // verify
        userFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertEquals( Locale.FRENCH, userFields.getLocale() );
        assertEquals( null, userFields.getFirstName() );
        assertEquals( null, userFields.getLastName() );
        assertEquals( null, userFields.getInitials() );
        assertEquals( null, userFields.getOrganization() );
    }

    @Test
    public void update_without_required_fields_on_local_user_store_throws_exception()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "required" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setFirstName( "First name" );
        userFields.setLastName( "Last name" );
        userFields.setInitials( "INI" );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "username", "testcreate" );
        formItems.putString( "password", "password" );
        formItems.putString( "email", "test@test.com" );
        formItems.putString( "first_name", "First name changed" );
        formItems.putString( "last_name", "Last name changed" );

        loginPortalUser( "testuser" );

        try
        {
            userHandlerController.handlerUpdate( request, response, session, formItems, null, null );
            fail( "Expected exception" );
        }
        catch ( Exception e )
        {
            assertTrue( e instanceof HttpServicesException );
            assertEquals( "Error in http services, error code: 400", e.getMessage() );
        }

    }

    @Test
    public void update_with_blank_required_fields_on_local_user_store_throws_exception()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "required" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setFirstName( "First name" );
        userFields.setLastName( "Last name" );
        userFields.setInitials( "INI" );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "username", "testcreate" );
        formItems.putString( "password", "password" );
        formItems.putString( "email", "test@test.com" );
        formItems.putString( "first_name", "First name changed" );
        formItems.putString( "last_name", "Last name changed" );
        formItems.putString( "initials", "  " ); // field set but blank

        loginPortalUser( "testuser" );

        try
        {
            userHandlerController.handlerUpdate( request, response, session, formItems, null, null );
            fail( "Expected exception" );
        }
        catch ( Exception e )
        {
            assertTrue( e instanceof HttpServicesException );
            assertEquals( "Error in http services, error code: 400", e.getMessage() );
        }

    }

    @Test
    public void update_with_empty_required_fields_on_local_user_store_throws_exception()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "required" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setFirstName( "First name" );
        userFields.setLastName( "Last name" );
        userFields.setInitials( "INI" );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = createExtendedMap();
        formItems.putString( "username", "testcreate" );
        formItems.putString( "password", "password" );
        formItems.putString( "email", "test@test.com" );
        formItems.putString( "first_name", "First name changed" );
        formItems.putString( "last_name", "Last name changed" );
        formItems.putString( "initials", "" ); // field set but empty

        loginPortalUser( "testuser" );

        try
        {
            userHandlerController.handlerUpdate( request, response, session, formItems, null, null );
            fail( "Expected exception" );
        }
        catch ( Exception e )
        {
            assertTrue( e instanceof HttpServicesException );
            assertEquals( "Error in http services, error code: 400", e.getMessage() );
        }

    }

    @Test
    // When update user with (unrequired) birthdate not sent (in formItems), the birthdate will be updated to empty
    public void update_with_unrequired_birthday_not_sent_becomes_empty()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "required" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setFirstName( "First name" );
        userFields.setLastName( "Last name" );
        userFields.setInitials( "INI" );
        userFields.setBirthday( DateUtil.parseDate( "12.12.2012" ) );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // verify
        UserFields resultUserFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertNotNull( resultUserFields.getBirthday() );
        assertEquals( "12.12.2012", DateUtil.formatDate( resultUserFields.getBirthday() ) );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = new ExtendedMap( true );
        formItems.putString( "first_name", "First name changed" );
        formItems.putString( "last_name", "Last name changed" );
        formItems.putString( "initials", "Initials changed" );
        formItems.putString( "email", "test@test.com" );

        loginPortalUser( "testuser" );

        userHandlerController.handlerUpdate( request, response, session, formItems, null, null );

        // verify
        resultUserFields = fixture.findUserByName( "testuser" ).getUserFields();
        assertNull( resultUserFields.getBirthday() );
    }

    @Test
    // When update user with birthdate not sent and birthdate required, there will be an exception
    public void update_with_required_birthday_not_sent_birthday_required_trows_exception()
        throws Exception
    {
        UserStoreConfig userStoreConfig = new UserStoreConfig();
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.FIRST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.LAST_NAME, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.INITIALS, "required" ) );
        userStoreConfig.addUserFieldConfig( createUserStoreUserFieldConfig( UserFieldType.BIRTHDAY, "required" ) );
        createLocalUserStore( "myLocalStore", true, userStoreConfig );

        fixture.flushAndClearHibernateSession();

        UserFields userFields = new UserFields();
        userFields.setFirstName( "First name" );
        userFields.setLastName( "Last name" );
        userFields.setInitials( "INI" );
        userFields.setBirthday( DateUtil.parseDate( "12.12.2012" ) );
        createNormalUser( "testuser", "myLocalStore", userFields );

        // verify
        Date birthday = fixture.findUserByName( "testuser" ).getUserFields().getBirthday();
        assertNotNull( birthday );
        assertEquals( "12.12.2012", DateUtil.formatDate( birthday ) );

        // exercise
        request.setAttribute( Attribute.ORIGINAL_SITEPATH, new SitePath( new SiteKey( 0 ), "/_services/user/create" ) );
        ExtendedMap formItems = new ExtendedMap( true );
        formItems.putString( "first_name", "First name changed" );
        formItems.putString( "last_name", "Last name changed" );
        formItems.putString( "initials", "Initials changed" );
        formItems.putString( "email", "test@test.com" );

        loginPortalUser( "testuser" );

        try
        {
            userHandlerController.handlerUpdate( request, response, session, formItems, null, null );
            fail( "Expected exception" );
        }
        catch ( Exception e )
        {
            assertTrue( e instanceof HttpServicesException );
            assertEquals( "Error in http services, error code: 400", e.getMessage() );
        }
    }

    private UserStoreConfigField createUserStoreUserFieldConfig( UserFieldType type, String properties )
    {
        UserStoreConfigField fieldConfig = new UserStoreConfigField( type );
        fieldConfig.setRemote( properties.contains( "remote" ) );
        fieldConfig.setReadOnly( properties.contains( "read-only" ) );
        fieldConfig.setRequired( properties.contains( "required" ) );
        fieldConfig.setIso( properties.contains( "iso" ) );
        return fieldConfig;
    }

    private void loginPortalUser( String userName )
    {
        PortalSecurityHolder.setImpersonatedUser( fixture.findUserByName( userName ).getKey() );
        PortalSecurityHolder.setLoggedInUser( fixture.findUserByName( userName ).getKey() );
    }

    private UserStoreKey createLocalUserStore( String name, boolean defaultStore, UserStoreConfig config )
    {
        StoreNewUserStoreCommand command = new StoreNewUserStoreCommand();
        command.setStorer( fixture.findUserByName( "admin" ).getKey() );
        command.setName( name );
        command.setDefaultStore( defaultStore );
        command.setConfig( config );
        return userStoreService.storeNewUserStore( command );
    }

    private UserKey createNormalUser( String userName, String userStoreName, UserFields userFields )
    {
        StoreNewUserCommand command = new StoreNewUserCommand();
        command.setStorer( fixture.findUserByName( "admin" ).getKey() );
        command.setUsername( userName );
        command.setUserStoreKey( fixture.findUserStoreByName( userStoreName ).getKey() );
        command.setAllowAnyUserAccess( true );
        command.setEmail( userName + "@example.com" );
        command.setPassword( "password" );
        command.setType( UserType.NORMAL );
        command.setDisplayName( userName );
        command.setUserFields( userFields );

        return userStoreService.storeNewUser( command );
    }

    private ExtendedMap createExtendedMap()
    {
        return new ExtendedMap( true );
    }
}
