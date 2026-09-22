package club.thatpetbff.gramophone.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import club.thatpetbff.gramophone.util.ServerUrlUtil;

import org.jellyfin.apiclient.model.users.AuthenticationResult;

import java.util.UUID;

@Entity(tableName = "users")
public class User {
    @NonNull
    @PrimaryKey
    public String id;
    public String name;

    public String server;
    public String token;

    public User() {
        this.id = UUID.randomUUID().toString();
    }

    public User(AuthenticationResult result, String server) {
        this.id = result.getUser().getId();
        this.name = result.getUser().getName();

        this.server = ServerUrlUtil.normalize(server);
        this.token = result.getAccessToken();
    }
}
