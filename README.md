# KIS Čtečka pro Android

Tato úžasná mobilní aplikace, která umí načítat *ISICy*, *KachnaKarty* a pravděpodobně i jiné *NFC podivnosti*, byla implementována jako náhrada *Kotyho HW čteček*, které postupně asi samy umřely.

Tato aplikace poskytuje webserver s websocketem na `ws://0.0.0.0:8080`, časem bude možná možnost toto nastavení konfigurovat.
Zatím tato aplikace umí pouze nezabezpečené připojení, takže je nutné ji mít postavenou za reverzní proxy (např. nginx).
