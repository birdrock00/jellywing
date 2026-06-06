package com.dkanada.gramophone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.model.User;

import org.junit.Test;

public class ModelSmokeTest {
    @Test
    public void newSongsReceiveUniqueStableIds() {
        Song first = new Song();
        Song second = new Song();

        assertNotNull(first.id);
        assertNotNull(second.id);
        assertNotEquals(first.id, second.id);
        assertEquals(first.id, first.toString());
    }

    @Test
    public void songIdentityIsBasedOnlyOnId() {
        Song first = new Song();
        first.id = "song-id";
        first.title = "Before";

        Song second = new Song();
        second.id = "song-id";
        second.title = "After";

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        second.id = "other-song-id";
        assertNotEquals(first, second);
    }

    @Test
    public void userDefaultConstructorCreatesEmptyPersistableUser() {
        User user = new User();

        assertNotNull(user.id);
        assertFalse(user.id.trim().isEmpty());
        assertTrue("name is optional until authentication fills it", user.name == null);
        assertTrue("server is optional until authentication fills it", user.server == null);
        assertTrue("token is optional until authentication fills it", user.token == null);
    }
}
