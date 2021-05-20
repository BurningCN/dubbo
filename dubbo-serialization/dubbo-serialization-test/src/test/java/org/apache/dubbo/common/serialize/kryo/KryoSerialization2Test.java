package org.apache.dubbo.common.serialize.kryo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.common.serialize.kryo.optimized.KryoSerialization2;
import org.apache.dubbo.common.serialize.model.person.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author BurningCN
 * @date 2021/5/20 10:05
 */
public class KryoSerialization2Test {
    protected Serialization serialization = new KryoSerialization2();
    protected ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    protected URL url = new URL("protocol", "1.1.1.1", 1234);
    // ================ Primitive Type ================
    protected BigPerson bigPerson;
    {
        bigPerson = new BigPerson();
        bigPerson.setPersonId("superman111");
        bigPerson.setLoginName("superman");
        bigPerson.setStatus(PersonStatus.ENABLED);
        bigPerson.setEmail("sm@1.com");
        bigPerson.setPenName("pname");
        ArrayList<Phone> phones = new ArrayList<Phone>();
        Phone phone1 = new Phone("86", "0571", "87654321", "001");
        Phone phone2 = new Phone("86", "0571", "87654322", "002");
        phones.add(phone1);
        phones.add(phone2);
        PersonInfo pi = new PersonInfo();
        pi.setPhones(phones);
        Phone fax = new Phone("86", "0571", "87654321", null);
        pi.setFax(fax);
        FullAddress addr = new FullAddress("CN", "zj", "3480", "wensanlu", "315000");
        pi.setFullAddress(addr);
        pi.setMobileNo("13584652131");
        pi.setMale(true);
        pi.setDepartment("b2b");
        pi.setHomepageUrl("www.capcom.com");
        pi.setJobTitle("qa");
        pi.setName("superman");
        bigPerson.setInfoProfile(pi);
    }
    @Test
    public void testObject() throws IOException, ClassNotFoundException {
        ObjectOutput objectOutput = serialization.serialize(url, byteArrayOutputStream);
        objectOutput.writeObject(bigPerson);
        objectOutput.flushBuffer();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                byteArrayOutputStream.toByteArray());
        ObjectInput deserialize = serialization.deserialize(url, byteArrayInputStream);
        assertEquals(bigPerson, BigPerson.class.cast(deserialize.readObject(BigPerson.class)));
        try {
            deserialize.readObject(BigPerson.class);
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testObjectWithAttachments() throws IOException, ClassNotFoundException {
        ObjectOutput objectOutput = serialization.serialize(url, byteArrayOutputStream);
        objectOutput.writeObject(bigPerson);

        Map<String, Object> attachments = new HashMap<>();
        attachments.put("attachments","attachments");
        objectOutput.writeObject(attachments);

        objectOutput.flushBuffer();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                byteArrayOutputStream.toByteArray());
        ObjectInput deserialize = serialization.deserialize(url, byteArrayInputStream);

        assertEquals(bigPerson, BigPerson.class.cast(deserialize.readObject(BigPerson.class)));
        assertEquals(attachments, deserialize.readAttachments());

        try {
            deserialize.readObject(BigPerson.class);
            fail();
        } catch (IOException expected) {
        }
    }

}