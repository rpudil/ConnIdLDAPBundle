/* 
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2011 ConnId.
 */
package net.tirasa.connid.bundles.ldap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoUtil;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import net.tirasa.connid.bundles.ldap.schema.LdapSchemaMapping;
import org.identityconnectors.test.common.TestHelpers;
import net.tirasa.connid.bundles.ldap.commons.LdapConstants;
import net.tirasa.connid.bundles.ldap.commons.LdapUtil;
import org.identityconnectors.common.CollectionUtil;
import org.junit.Test;

public class AdapterCompatibilityTests extends LdapConnectorTestBase {

    // TODO test authenticate.
    @Override
    protected boolean restartServerAfterEachTest() {
        return true;
    }

    @Test
    public void testAccountOperationalAttributes() {
        ConnectorFacade facade = newFacade();
        ObjectClassInfo oci = facade.schema().findObjectClassInfo(
                ObjectClass.ACCOUNT_NAME);

        AttributeInfo info = AttributeInfoUtil.find(
                OperationalAttributes.PASSWORD_NAME, oci.getAttributeInfo());
        assertEquals(LdapConstants.PASSWORD, info);
    }

    @Test
    public void testAccountAttributes() {
        ConnectorFacade facade = newFacade();
        ConnectorObject user0 = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(USER_0_DN), "uid", "cn", "givenName", "sn");

        assertEquals(USER_0_DN, user0.getName().getNameValue());
        assertEquals(USER_0_UID, AttributeUtil.getAsStringValue(user0.
                getAttributeByName("uid")));
        assertEquals(USER_0_CN, AttributeUtil.getAsStringValue(user0.
                getAttributeByName("cn")));
        assertEquals(USER_0_GIVEN_NAME, AttributeUtil.getAsStringValue(user0.
                getAttributeByName("givenName")));
        assertEquals(USER_0_SN, AttributeUtil.getAsStringValue(user0.
                getAttributeByName("sn")));
    }

    @Test
    public void testGroupAttributes() {
        ConnectorFacade facade = newFacade();
        ConnectorObject object = searchByAttribute(facade, ObjectClass.GROUP,
                new Name(UNIQUE_BUGS_AND_FRIENDS_DN), "cn", "uniqueMember");

        assertEquals(UNIQUE_BUGS_AND_FRIENDS_CN,
                AttributeUtil.getAsStringValue(object.getAttributeByName("cn")));
        Attribute uniqueMember = object.getAttributeByName("uniqueMember");
        assertTrue(uniqueMember.getValue().contains(BUGS_BUNNY_DN));
    }

    @Test
    public void testOrganizationAttributes() {
        ConnectorFacade facade = newFacade();
        ConnectorObject object = searchByAttribute(facade, new ObjectClass(
                "organization"), new Name(ACME_DN), "dn", "o", "objectClass");

        assertEquals(ACME_DN, object.getName().getNameValue());
        assertEquals(ACME_DN, AttributeUtil.getAsStringValue(object.
                getAttributeByName("dn")));
        assertEquals(ACME_O, AttributeUtil.getAsStringValue(object.
                getAttributeByName("o")));
    }

    @Test
    public void testInetOrgPersonAttributes() {
        // The LDAP edit group form does exactly this operation.

        LdapConfiguration config = newConfiguration();
        config.setBaseContexts(ACME_DN);
        ConnectorFacade facade = newFacade(config);

        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("cn", "dn");
        List<ConnectorObject> objects = TestHelpers.searchToList(facade,
                new ObjectClass("inetOrgPerson"), null, builder.build());

        ConnectorObject object = findByAttribute(objects, "dn", BUGS_BUNNY_DN);
        assertEquals(BUGS_BUNNY_CN, AttributeUtil.getStringValue(object.
                getAttributeByName("cn")));
    }

    @Test
    public void testCreateGroupOfUniqueNamesWithoutMembers() {
        LdapConfiguration config = newConfiguration();
        ConnectorFacade facade = newFacade(config);

        ObjectClass oclass = new ObjectClass("groupOfUniqueNames");
        Set<Attribute> attributes = new HashSet<Attribute>();
        Name name = new Name("cn=Another Group," + ACME_DN);
        attributes.add(name);
        attributes.add(AttributeBuilder.build("cn", "Another Group"));
        // If "uniqueMember" is sent to the server as an empty attribute, the server complains.
        // The test makes sure it is not sent at all.
        attributes.add(AttributeBuilder.build("uniqueMember", Collections.emptyList()));
        Uid uid = facade.create(oclass, attributes, null);

        ConnectorObject newGroup = facade.getObject(oclass, uid, null);
        assertEquals(name, newGroup.getName());
    }

    @Test
    public void testRetriveLdapGroups() {
        ConnectorFacade facade = newFacade();
        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(SYLVESTER_DN), LdapConstants.LDAP_GROUPS_NAME);
        assertAttributeValue(CollectionUtil.newList(UNIQUE_BUGS_AND_FRIENDS_DN,
                UNIQUE_EXTERNAL_PEERS_DN),
                object.getAttributeByName(LdapConstants.LDAP_GROUPS_NAME));

        LdapConfiguration config = newConfiguration();
        config.setGroupMemberAttribute("member");
        facade = newFacade(config);
        object = searchByAttribute(facade, ObjectClass.ACCOUNT, new Name(
                SYLVESTER_DN), LdapConstants.LDAP_GROUPS_NAME);
        assertAttributeValue(CollectionUtil.newList(BUGS_AND_FRIENDS_DN, EXTERNAL_PEERS_DN),
                object.getAttributeByName(LdapConstants.LDAP_GROUPS_NAME));
    }

    @Test
    public void testRetrivePosixGroups() {
        ConnectorFacade facade = newFacade();
        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(SYLVESTER_DN), LdapConstants.POSIX_GROUPS_NAME);
        assertAttributeValue(CollectionUtil.newList(POSIX_BUGS_AND_FRIENDS_DN,
                POSIX_EXTERNAL_PEERS_DN),
                object.getAttributeByName(LdapConstants.POSIX_GROUPS_NAME));
    }

    @Test
    public void testCreateWithUniqueLdapGroups() {
        ConnectorFacade facade = newFacade();

        Attribute groupsAttr = AttributeBuilder.build(
                LdapConstants.LDAP_GROUPS_NAME,
                UNIQUE_BUGS_AND_FRIENDS_DN, UNIQUE_EXTERNAL_PEERS_DN);
        doTestCreateWithGroups(facade, groupsAttr);
    }

    @Test
    public void testCreateWithLdapGroups() {
        ConnectorFacade facade = newFacade();
        LdapConfiguration config = newConfiguration();
        config.setGroupMemberAttribute("member"); // For groupOfNames.
        facade = newFacade(config);

        Attribute groupsAttr = AttributeBuilder.build(
                LdapConstants.LDAP_GROUPS_NAME,
                BUGS_AND_FRIENDS_DN, EXTERNAL_PEERS_DN);
        doTestCreateWithGroups(facade, groupsAttr);
    }

    @Test
    public void testCreateWithPosixGroups() {
        ConnectorFacade facade = newFacade();

        Attribute groupsAttr = AttributeBuilder.build(
                LdapConstants.POSIX_GROUPS_NAME,
                POSIX_BUGS_AND_FRIENDS_DN, POSIX_EXTERNAL_PEERS_DN);
        doTestCreateWithGroups(facade, groupsAttr);
    }

    private void doTestCreateWithGroups(ConnectorFacade facade, Attribute groupsAttr) {
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(new Name("uid=porky.pig," + ACME_USERS_DN));
        attributes.add(AttributeBuilder.build("uid", "porky.pig"));
        attributes.add(AttributeBuilder.build("cn", "Porky Pig"));
        attributes.add(AttributeBuilder.build("givenName", "Porky"));
        attributes.add(AttributeBuilder.build("sn", "Pig"));
        attributes.add(groupsAttr);
        Uid uid = facade.create(ObjectClass.ACCOUNT, attributes, null);

        assertAttributeValue(groupsAttr.getValue(), facade, ObjectClass.ACCOUNT,
                uid, groupsAttr.getName());
    }

    @Test
    public void testAddLdapGroups() {
        ConnectorFacade facade = newFacade();

        Attribute groupsAttr = AttributeBuilder.build(
                LdapConstants.LDAP_GROUPS_NAME, UNIQUE_EMPTY_GROUP_DN);
        doTestAddGroups(facade, groupsAttr);
    }

    @Test
    public void testAddPosixGroups() {
        ConnectorFacade facade = newFacade();

        Attribute groupsAttr = AttributeBuilder.build(
                LdapConstants.POSIX_GROUPS_NAME, POSIX_EMPTY_GROUP_DN);
        doTestAddGroups(facade, groupsAttr);
    }

    private void doTestAddGroups(ConnectorFacade facade, Attribute groupsAttr) {
        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(BUGS_BUNNY_DN), groupsAttr.getName());
        List<Object> oldGroups = CollectionUtil.newList(object.getAttributeByName(groupsAttr.
                getName()).getValue());

        Uid uid = facade.addAttributeValues(ObjectClass.ACCOUNT, object.getUid(),
                Collections.singleton(groupsAttr), null);

        oldGroups.addAll(groupsAttr.getValue());
        assertAttributeValue(oldGroups, facade, ObjectClass.ACCOUNT, uid,
                groupsAttr.getName());
    }

    @Test
    public void testUpdateUniqueLdapGroups() {
        ConnectorFacade facade = newFacade();

        Attribute groupsAttr = AttributeBuilder.build(
                LdapConstants.LDAP_GROUPS_NAME, UNIQUE_EXTERNAL_PEERS_DN);
        doTestUpdateGroups(facade, groupsAttr);
    }

    @Test
    public void testUpdatePosixGroups() {
        ConnectorFacade facade = newFacade();

        Attribute groupsAttr = AttributeBuilder.build(
                LdapConstants.LDAP_GROUPS_NAME, UNIQUE_EXTERNAL_PEERS_DN);
        doTestUpdateGroups(facade, groupsAttr);
    }

    private void doTestUpdateGroups(ConnectorFacade facade, Attribute groupsAttr) {
        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(SYLVESTER_DN), groupsAttr.getName());

        Uid uid = facade.update(ObjectClass.ACCOUNT, object.getUid(), Collections.singleton(groupsAttr), null);

        assertAttributeValue(groupsAttr.getValue(), facade, ObjectClass.ACCOUNT, uid, groupsAttr.getName());
    }

    @Test
    public void testRemoveUniqueLdapGroups() {
        ConnectorFacade facade = newFacade();

        Attribute groupsAttr = AttributeBuilder.build(LdapConstants.LDAP_GROUPS_NAME, UNIQUE_EXTERNAL_PEERS_DN);
        doTestRemoveGroups(facade, groupsAttr);
    }

    @Test
    public void testRemovePosixGroups() {
        ConnectorFacade facade = newFacade();

        Attribute groupsAttr = AttributeBuilder.build(LdapConstants.POSIX_GROUPS_NAME, POSIX_EXTERNAL_PEERS_DN);
        doTestRemoveGroups(facade, groupsAttr);
    }

    private void doTestRemoveGroups(ConnectorFacade facade, Attribute groupsAttr) {
        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(SYLVESTER_DN), groupsAttr.getName());
        List<Object> oldGroups = CollectionUtil.newList(object.getAttributeByName(groupsAttr.getName()).getValue());

        Uid uid = facade.removeAttributeValues(
                ObjectClass.ACCOUNT, object.getUid(), Collections.singleton(groupsAttr), null);

        oldGroups.removeAll(groupsAttr.getValue());
        assertAttributeValue(oldGroups, facade, ObjectClass.ACCOUNT, uid,
                groupsAttr.getName());
    }

    @Test(expected = ConnectorException.class)
    public void testCannotRemoveUidWhenInPosixGroups() {
        ConnectorFacade facade = newFacade();

        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(BUGS_BUNNY_DN));
        Attribute uidAttr = AttributeBuilder.build("uid", BBUNNY_UID);
        facade.removeAttributeValues(ObjectClass.ACCOUNT, object.getUid(), Collections.singleton(uidAttr), null);
    }

    @Test(expected = ConnectorException.class)
    public void testCannotUpdateUidToNoneWhenInPosixGroups() {
        LdapConfiguration config = newConfiguration();
        config.setBaseContexts(ACME_DN, SMALL_COMPANY_DN);
        ConnectorFacade facade = newFacade(config);

        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(OWNER_DN));
        Attribute uidAttr = AttributeBuilder.build("uid");
        facade.update(ObjectClass.ACCOUNT, object.getUid(), Collections.singleton(uidAttr), null);
    }

    @Test
    public void testRenameMaintainsGroupMemberships() {
        LdapConfiguration config = newConfiguration();
        config.setMaintainLdapGroupMembership(true);
        config.setMaintainPosixGroupMembership(true);
        ConnectorFacade facade = newFacade(config);

        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(SYLVESTER_DN),
                LdapConstants.LDAP_GROUPS_NAME, LdapConstants.POSIX_GROUPS_NAME);
        List<String> oldLdapGroups = LdapUtil.checkedListByFilter(
                object.getAttributeByName(LdapConstants.LDAP_GROUPS_NAME).getValue(), String.class);
        List<String> oldPosixGroups = LdapUtil.checkedListByFilter(
                object.getAttributeByName(LdapConstants.POSIX_GROUPS_NAME).getValue(), String.class);

        Name newName = new Name("uid=sylvester.the.cat," + ACME_USERS_DN);
        Uid uid = facade.update(ObjectClass.ACCOUNT, object.getUid(),
                Collections.singleton((Attribute) newName), null);

        object = searchByAttribute(facade, ObjectClass.ACCOUNT, uid,
                LdapConstants.LDAP_GROUPS_NAME, LdapConstants.POSIX_GROUPS_NAME);
        assertAttributeValue(CollectionUtil.newList(UNIQUE_BUGS_AND_FRIENDS_DN, UNIQUE_EXTERNAL_PEERS_DN),
                object.getAttributeByName(LdapConstants.LDAP_GROUPS_NAME));
        assertAttributeValue(CollectionUtil.newList(POSIX_BUGS_AND_FRIENDS_DN, POSIX_EXTERNAL_PEERS_DN),
                object.getAttributeByName(LdapConstants.POSIX_GROUPS_NAME));

        // Also need to test that the old entries were actually removed from the old groups.
        for (String group : oldLdapGroups) {
            object = searchByAttribute(facade, new ObjectClass(
                    "groupOfUniqueNames"), new Name(group), "uniqueMember");
            List<Object> members = object.getAttributeByName("uniqueMember").
                    getValue();
            assertFalse("Group " + group + " should not contain " + SYLVESTER_DN,
                    members.contains(SYLVESTER_DN));
        }
        for (String group : oldPosixGroups) {
            object = searchByAttribute(facade, new ObjectClass("posixGroup"),
                    new Name(group), "memberUid");
            List<Object> members = object.getAttributeByName("memberUid").
                    getValue();
            assertFalse(
                    "Group " + group + " should not contain " + SYLVESTER_UID,
                    members.contains(SYLVESTER_UID));
        }
    }

    @Test
    public void testRenameAndUpdateGroupMemberships() {
        LdapConfiguration config = newConfiguration();
        config.setMaintainLdapGroupMembership(true);
        config.setMaintainPosixGroupMembership(true);
        ConnectorFacade facade = newFacade(config);

        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(SYLVESTER_DN),
                LdapConstants.LDAP_GROUPS_NAME, LdapConstants.POSIX_GROUPS_NAME);
        List<String> oldLdapGroups = LdapUtil.checkedListByFilter(object.
                getAttributeByName(LdapConstants.LDAP_GROUPS_NAME).getValue(),
                String.class);
        List<String> oldPosixGroups = LdapUtil.checkedListByFilter(object.
                getAttributeByName(LdapConstants.POSIX_GROUPS_NAME).getValue(),
                String.class);

        Name newName = new Name("uid=sylvester.the.cat," + ACME_USERS_DN);
        Attribute ldapGroupsAttr = AttributeBuilder.build(
                LdapConstants.LDAP_GROUPS_NAME,
                UNIQUE_EXTERNAL_PEERS_DN, UNIQUE_EMPTY_GROUP_DN);
        Attribute posixGroupsAttr = AttributeBuilder.build(
                LdapConstants.POSIX_GROUPS_NAME,
                POSIX_EXTERNAL_PEERS_DN, POSIX_EMPTY_GROUP_DN);
        Uid uid = facade.update(ObjectClass.ACCOUNT, object.getUid(),
                CollectionUtil.newSet(newName, ldapGroupsAttr, posixGroupsAttr), null);

        object = searchByAttribute(facade, ObjectClass.ACCOUNT, uid,
                LdapConstants.LDAP_GROUPS_NAME, LdapConstants.POSIX_GROUPS_NAME);
        assertAttributeValue(ldapGroupsAttr.getValue(), object.
                getAttributeByName(LdapConstants.LDAP_GROUPS_NAME));
        assertAttributeValue(posixGroupsAttr.getValue(), object.
                getAttributeByName(LdapConstants.POSIX_GROUPS_NAME));

        // Also need to test that the old entries were actually removed from the old groups.
        for (String group : oldLdapGroups) {
            object = searchByAttribute(facade, new ObjectClass(
                    "groupOfUniqueNames"), new Name(group), "uniqueMember");
            List<Object> members = object.getAttributeByName("uniqueMember").
                    getValue();
            assertFalse("Group " + group + " should not contain " + SYLVESTER_DN,
                    members.contains(SYLVESTER_DN));
        }
        for (String group : oldPosixGroups) {
            object = searchByAttribute(facade, new ObjectClass("posixGroup"),
                    new Name(group), "memberUid");
            List<Object> members = object.getAttributeByName("memberUid").
                    getValue();
            assertFalse(
                    "Group " + group + " should not contain " + SYLVESTER_UID,
                    members.contains(SYLVESTER_UID));
        }
    }

    @Test
    public void testRenameDoesNotMaintainGroupMembershipsUnlessConfigured() {
        LdapConfiguration config = newConfiguration();
        assertFalse(config.isMaintainLdapGroupMembership());
        assertFalse(config.isMaintainPosixGroupMembership());
        ConnectorFacade facade = newFacade(config);

        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(SYLVESTER_DN));
        String newUid = "sylvester.the.cat";
        String newEntryDN = "uid=" + newUid + "," + ACME_USERS_DN;
        facade.update(ObjectClass.ACCOUNT, object.getUid(),
                Collections.singleton((Attribute) new Name(newEntryDN)), null);

        object = searchByAttribute(facade, new ObjectClass("groupOfUniqueNames"),
                new Name(UNIQUE_BUGS_AND_FRIENDS_DN), "uniqueMember");
        List<Object> members = object.getAttributeByName("uniqueMember").
                getValue();
        assertTrue(members.contains(SYLVESTER_DN));
        assertFalse(members.contains(newEntryDN));

        object = searchByAttribute(facade, new ObjectClass("groupOfUniqueNames"),
                new Name(UNIQUE_EXTERNAL_PEERS_DN), "uniqueMember");
        members = object.getAttributeByName("uniqueMember").getValue();
        assertTrue(members.contains(SYLVESTER_DN));
        assertFalse(members.contains(newEntryDN));

        object = searchByAttribute(facade, new ObjectClass("posixGroup"),
                new Name(POSIX_BUGS_AND_FRIENDS_DN), "memberUid");
        members = object.getAttributeByName("memberUid").getValue();
        assertTrue(members.contains(SYLVESTER_UID));
        assertFalse(members.contains(newUid));

        object = searchByAttribute(facade, new ObjectClass("posixGroup"),
                new Name(POSIX_EXTERNAL_PEERS_DN), "memberUid");
        members = object.getAttributeByName("memberUid").getValue();
        assertTrue(members.contains(SYLVESTER_UID));
        assertFalse(members.contains(newUid));
    }

    @Test
    public void testDeleteMaintainsGroupMemberships() {
        LdapConfiguration config = newConfiguration();
        config.setMaintainLdapGroupMembership(true);
        config.setMaintainPosixGroupMembership(true);
        ConnectorFacade facade = newFacade(config);

        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(SYLVESTER_DN),
                LdapConstants.LDAP_GROUPS_NAME, LdapConstants.POSIX_GROUPS_NAME);
        List<String> oldLdapGroups = LdapUtil.checkedListByFilter(object.
                getAttributeByName(LdapConstants.LDAP_GROUPS_NAME).getValue(),
                String.class);
        List<String> oldPosixGroups = LdapUtil.checkedListByFilter(object.
                getAttributeByName(LdapConstants.POSIX_GROUPS_NAME).getValue(),
                String.class);

        facade.delete(ObjectClass.ACCOUNT, object.getUid(), null);

        // Need to test that the old entries were actually removed from the old groups.
        for (String group : oldLdapGroups) {
            object = searchByAttribute(facade, new ObjectClass(
                    "groupOfUniqueNames"), new Name(group), "uniqueMember");
            List<Object> members = object.getAttributeByName("uniqueMember").
                    getValue();
            assertFalse("Group " + group + " should not contain " + SYLVESTER_DN,
                    members.contains(SYLVESTER_DN));
        }
        for (String group : oldPosixGroups) {
            object = searchByAttribute(facade, new ObjectClass("posixGroup"),
                    new Name(group), "memberUid");
            List<Object> members = object.getAttributeByName("memberUid").
                    getValue();
            assertFalse(
                    "Group " + group + " should not contain " + SYLVESTER_UID,
                    members.contains(SYLVESTER_UID));
        }
    }

    @Test
    public void testDeleteDoesNotMaintainGroupMembershipsUnlessConfigured() {
        LdapConfiguration config = newConfiguration();
        assertFalse(config.isMaintainLdapGroupMembership());
        assertFalse(config.isMaintainPosixGroupMembership());
        ConnectorFacade facade = newFacade(config);

        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                new Name(SYLVESTER_DN));
        facade.delete(ObjectClass.ACCOUNT, object.getUid(), null);

        object = searchByAttribute(facade, new ObjectClass("groupOfUniqueNames"),
                new Name(UNIQUE_BUGS_AND_FRIENDS_DN), "uniqueMember");
        List<Object> members = object.getAttributeByName("uniqueMember").
                getValue();
        assertTrue(members.contains(SYLVESTER_DN));

        object = searchByAttribute(facade, new ObjectClass("groupOfUniqueNames"),
                new Name(UNIQUE_EXTERNAL_PEERS_DN), "uniqueMember");
        members = object.getAttributeByName("uniqueMember").getValue();
        assertTrue(members.contains(SYLVESTER_DN));

        object = searchByAttribute(facade, new ObjectClass("posixGroup"),
                new Name(POSIX_BUGS_AND_FRIENDS_DN), "memberUid");
        members = object.getAttributeByName("memberUid").getValue();
        assertTrue(members.contains(SYLVESTER_UID));

        object = searchByAttribute(facade, new ObjectClass("posixGroup"),
                new Name(POSIX_EXTERNAL_PEERS_DN), "memberUid");
        members = object.getAttributeByName("memberUid").getValue();
        assertTrue(members.contains(SYLVESTER_UID));
    }

    @Test
    public void testPasswordHashing()
            throws Exception {
        LdapConfiguration config = newConfiguration();
        config.setPasswordHashAlgorithm("SHA");
        ConnectorFacade facade = newFacade(config);

        doTestPasswordHashing(facade, "SHA");
    }

    @Test
    public void testSaltedPasswordHashing()
            throws Exception {
        LdapConfiguration config = newConfiguration();
        config.setPasswordHashAlgorithm("SSHA");
        ConnectorFacade facade = newFacade(config);

        doTestPasswordHashing(facade, "SSHA");
    }

    private void doTestPasswordHashing(ConnectorFacade facade, String algorithm)
            throws UnsupportedEncodingException {
        String algorithmLabel = "{" + algorithm + "}";

        Set<Attribute> attrs = new HashSet<Attribute>();
        attrs.add(new Name("uid=daffy.duck," + ACME_USERS_DN));
        attrs.add(AttributeBuilder.build("uid", "daffy.duck"));
        attrs.add(AttributeBuilder.build("cn", "Daffy Duck"));
        attrs.add(AttributeBuilder.build("sn", "Duck"));
        attrs.add(AttributeBuilder.build("ds-pwp-password-policy-dn",
                "cn=Clear Text Password Policy,cn=Password Policies,cn=config"));
        GuardedString password = new GuardedString("foobar".toCharArray());
        attrs.add(AttributeBuilder.buildPassword(password));
        Uid uid = facade.create(ObjectClass.ACCOUNT, attrs, null);

        ConnectorObject object = searchByAttribute(facade, ObjectClass.ACCOUNT,
                uid, "userPassword");
        byte[] passwordBytes = (byte[]) object.getAttributeByName("userPassword").
                getValue().get(0);
        assertTrue(new String(passwordBytes, "UTF-8").startsWith(algorithmLabel));
        facade.authenticate(ObjectClass.ACCOUNT, "daffy.duck", password, null);

        password = new GuardedString("newpassword".toCharArray());
        facade.update(ObjectClass.ACCOUNT, object.getUid(),
                Collections.singleton(AttributeBuilder.buildPassword(password)), null);

        object = searchByAttribute(facade, ObjectClass.ACCOUNT, uid,
                "userPassword");
        passwordBytes = (byte[]) object.getAttributeByName("userPassword").
                getValue().get(0);
        assertTrue(new String(passwordBytes, "UTF-8").startsWith(algorithmLabel));
        facade.authenticate(ObjectClass.ACCOUNT, "daffy.duck", password, null);
    }

    @Test
    public void testSearchAnyObjectClass() {
        ConnectorFacade facade = newFacade();

        Filter filter = FilterBuilder.equalTo(AttributeBuilder.build(Name.NAME,
                BUGS_BUNNY_DN));
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet("cn", "dn");
        List<ConnectorObject> objects = TestHelpers.searchToList(facade,
                LdapSchemaMapping.ANY_OBJECT_CLASS, filter, builder.build());

        ConnectorObject bunny = objects.get(0);
        assertEquals(LdapSchemaMapping.ANY_OBJECT_CLASS, bunny.getObjectClass());
        assertEquals(BUGS_BUNNY_DN, bunny.getName().getNameValue());
        assertEquals(BUGS_BUNNY_CN, bunny.getAttributeByName("cn").getValue().
                get(0));
    }

    @Test
    public void testSearchFilter() {
        ConnectorFacade facade = newFacade();
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setOption(LdapConstants.SEARCH_FILTER_NAME,
                "(uid=" + BUGS_BUNNY_UID + ")");
        List<ConnectorObject> objects = TestHelpers.searchToList(facade,
                ObjectClass.ACCOUNT, null, builder.build());

        assertEquals(1, objects.size());
        ConnectorObject bunny = objects.get(0);
        assertEquals(BUGS_BUNNY_DN, bunny.getName().getNameValue());
    }

    private static void assertAttributeValue(List<?> expected, ConnectorFacade facade, ObjectClass oclass, Uid uid,
            String attrName) {
        ConnectorObject object = searchByAttribute(facade, oclass, uid, attrName);
        assertAttributeValue(expected, object.getAttributeByName(attrName));
    }

    private static void assertAttributeValue(List<?> expected, Attribute attr) {
        Set<?> attrValue = CollectionUtil.newSet(attr.getValue());
        Set<?> expectedValue = CollectionUtil.newSet(expected);
        assertEquals(expectedValue, attrValue);
    }
}
