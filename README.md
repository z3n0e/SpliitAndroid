# Spliit per Android (client non ufficiale)

App Android nativa per [Spliit](https://spliit.app), il servizio open source per dividere le spese.
Usa lo stesso server del sito: i gruppi creati qui si aprono anche dal web e dall'app iOS, e viceversa.

## Cosa fa

- **Gruppi**: crea un gruppo (nome, valuta, partecipanti, note), entra con un link, preferiti, rimozione dal telefono.
- **Spese**: elenco con ricerca e caricamento a pagine, aggiunta, modifica ed eliminazione.
  **Importo in un'altra valuta**: scrivi quanto hai pagato (es. 250.000 VND) e l'app lo converte nella valuta del
  gruppo con il tasso del giorno della spesa. Usa gli stessi campi di Spliit, quindi sul sito e su iOS la spesa
  mostra importo originale, valuta e tasso come se l'avessi inserita lì.
  Divisione in parti uguali, per quote, in percentuale o per importi, con anteprima di quanto tocca a ciascuno.
  Data, categoria, chi ha pagato, rimborso, spese ricorrenti, note, apertura delle ricevute allegate.
- **Saldi**: chi è in credito e chi in debito, rimborsi suggeriti e "Segna pagato" in un tocco.
- **Attività**: cronologia delle modifiche del gruppo.
- **Riepilogo**: informazioni del gruppo, esportazione CSV/JSON, totale del gruppo, quanto hai pagato tu, la tua quota, spesa per categoria.
- **Chi sei**: scegli il tuo nome nel gruppo per vedere il tuo saldo in evidenza (anche nella home).
- **Link**: apre i link `https://spliit.app/groups/...` e accetta "Condividi" da altre app.
- **Server self-hosted**: puoi puntare l'app alla tua istanza (solo https).

## Design

Material 3 Expressive (`MaterialExpressiveTheme`, motion "expressive", liste segmentate, `LoadingIndicator`,
barra di navigazione corta, top app bar flessibile), colori dinamici Material You su Android 12+ e tema chiaro/scuro.

## Batteria e prestazioni

- Nessun servizio in background, nessun polling, nessun WorkManager: la rete si usa solo quando apri o aggiorni una schermata.
- All'apertura di un gruppo partono in parallelo solo 3 richieste (gruppo, saldi, prima pagina di spese);
  attività e riepilogo si caricano solo se apri quelle schede.
- Dopo un salvataggio la schermata del gruppo si aggiorna da sola tramite un evento interno (niente ricarichi inutili).
- Client HTTP di sistema con connessioni keep-alive riutilizzate e gzip; nessuna libreria di rete.
- R8 con ottimizzazione completa e rimozione delle risorse inutilizzate; icone disegnate a mano invece della libreria "extended".

## Tassi di cambio

- Fonte principale: Frankfurter (dati della Banca centrale europea), lo stesso servizio usato dal sito di Spliit.
- Fonte di riserva per le valute che la BCE non copre (es. dong vietnamita): currency-api, con due indirizzi
  alternativi (jsDelivr e Cloudflare). Nessuna chiave, nessun account.
- Viene usato il tasso del giorno della spesa; i tassi di oggi si riaggiornano dopo 6 ore, quelli dei giorni passati
  non vengono mai richiesti due volte.
- Ogni tasso ottenuto resta salvato sul telefono: senza connessione l'app usa l'ultimo tasso valido e te lo dice.
  Puoi sempre correggere il tasso a mano o aggiornarlo con il pulsante ↻.
- La valuta proposta è l'ultima che hai usato, altrimenti quella del Paese della rete mobile in cui ti trovi
  (senza permessi di posizione).
- Serve che il gruppo abbia una valuta standard (es. EUR), non un simbolo personalizzato.

## Sicurezza e privacy

- Un solo permesso: `INTERNET`.
- Solo HTTPS, traffico in chiaro bloccato, fidati solo i certificati di sistema (niente CA installate dall'utente).
- Niente backup cloud né trasferimento dei dati locali (i link dei gruppi sono chiavi d'accesso).
- Nessuna WebView, nessun tracciamento, nessuna pubblicità, nessun log nelle build di rilascio.
- Input validati prima dell'invio, risposte del server limitate a 8 MB, link degli allegati aperti solo se https.

## Come ottenere l'APK

Non c'è bisogno di Android Studio.

1. Crea un repository **privato** su GitHub e carica il contenuto di questa cartella.
2. Vai su **Actions → Build APK → Run workflow** (parte anche a ogni push su `main`).
3. Al termine scarica l'artifact **spliit-apk** e installa il file `.apk` sul telefono
   (Android chiederà di consentire l'installazione da questa fonte).

### Firma (consigliata)

Senza configurazione l'APK è firmato con una chiave di debug generata dal runner: funziona, ma ogni build ha una
firma diversa, quindi per aggiornare dovrai disinstallare la versione precedente. Per aggiornamenti normali crea una
chiave tua una volta sola:

```bash
keytool -genkeypair -v -keystore release.jks -alias spliit -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks > release.jks.b64
```

Poi in **Settings → Secrets and variables → Actions** aggiungi:
`SIGNING_KEYSTORE_BASE64` (contenuto di `release.jks.b64`), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (`spliit`),
`SIGNING_KEY_PASSWORD`. Conserva `release.jks` in un posto sicuro e non metterlo nel repository.

### Con Android Studio

Apri la cartella; se manca lo script `gradlew`, esegui una volta `gradle wrapper --gradle-version 9.5.0`.
Poi **Build → Generate App Bundles or APKs → Build APK(s)**.

## Requisiti tecnici

Android 8.0 (API 26) o superiore. Build: AGP 9.3, Kotlin 2.4, Gradle 9.5, JDK 17+, compileSdk 37,
Compose Material 3 1.5 (Expressive).

## Note

- Su Android 12+ i link `spliit.app` si aprono nell'app solo dopo averlo consentito in
  **Impostazioni → App → Spliit → Apri per impostazione predefinita → Aggiungi link**
  (il dominio non è verificato per questa app). In alternativa usa "Condividi → Spliit" o incolla il link nella home.
- Caricare nuove ricevute e la conversione automatica di valuta restano sul web; l'app conserva quelle già presenti.
- Client non ufficiale. Spliit è un progetto open source con licenza MIT: https://github.com/spliit-app/spliit
