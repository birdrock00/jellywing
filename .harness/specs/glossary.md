# Glossary

Shared domain terms so every agent uses the same vocabulary. Add terms as the
project grows; link with [[wikilinks]] when using the Obsidian store.

- **Handoff** — the transition of a conversation from bot-handled to human-handled.
- **Take over** — the operator action that initiates a handoff.
- **Bot-handled** — state where the AI agent is replying autonomously.
- **Human-handled** — state where an operator is replying; the bot is paused.
- **Operator inbox** — the queue of conversations assigned to / awaiting operators.
- **Run** — one execution of an agent; its output lives in `progress/<run>/`.

Domain terms (E00-F01 bootstrap — the vocabulary this product's specs use):

- **Jellyfin server** — the remote media server jellywing connects to. Its
  library is read-only from the app's point of view (principle P1).
- **Server connection** — the saved triple of base URL, server user and
  access token that identifies jellywing to one Jellyfin server.
- **Library** — the media the app shows for the current server connection
  and the selected library/category filter.
- **Category** — a server-side library filter choice (`model/Category.java`);
  "filter content by library" in the README.
- **Language** — the audio-language filter (`model/Language.java`, surfaced
  as the `Language: ` genre prefix) used to scope albums/songs and queues.
- **Playlist** — a client-local ordered list of songs held in the app's own
  database, not a server entity.
- **Favorite** — a client-local star on a song, held in the app's own
  database; not written back to the server.
- **Cache** — a local copy of a song the app downloaded for offline play
  (per-song or batch); not a server-side download job.
- **Up next** — the app's own upcoming-playback queue, separate from the
  server's playback state.
