package client.user;

import java.io.Serializable;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;


// Required for storing and providing our user credentials (username + certificate)
@Getter
@Setter
public class UserContext implements User, Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private Set<String> roles;
    private String account;
    private String affiliation;
    private Enrollment enrollment;
    private String mspId;
    private String networkId;
}
