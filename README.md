# 🧩 Cruciverba

<p align="center">
  <a href="https://github.com/samuelemuccio/Cruciverba/actions/runs/39816839022/artifacts/12442104377">
    <img src="https://img.shields.io/badge/📲_SCARICA_APK-7C5CFC?style=for-the-badge&logo=android&logoColor=white" alt="Scarica APK Android">
  </a>
  <br>
  <sub>Scarica l'artifact della build Android da GitHub Actions.</sub>
</p>
  <a href="https://github.com/samuelemuccio/Cruciverba/actions/workflows/build-apk.yml">
    <img src="https://github.com/samuelemuccio/Cruciverba/actions/workflows/build-apk.yml/badge.svg" alt="Build Android APK">
  </a>
  <br><br>
  <a href="https://github.com/samuelemuccio/Cruciverba">Repository</a> ·
  <a href="https://github.com/samuelemuccio/Cruciverba/actions/workflows/build-apk.yml">Build e download</a> ·
  <a href="https://github.com/samuelemuccio/Cruciverba/issues">Segnala un problema</a>
</p>

---

## ✨ Funzionalità

- **Generazione intelligente:** crea più schemi e li ordina in base a incroci, densità, compattezza e simmetria.
- **Parola Misteriosa:** costruisce il cruciverba attorno a una parola o frase chiave, anche composta.
- **Importazione flessibile:** incolla elenchi di parole e definizioni in diversi formati; il parser riconosce separatori comuni e segnala le righe da correggere.
- **Liste salvate:** conserva e riutilizza i tuoi elenchi; puoi modificare, eliminare, riordinare o mescolare le parole.
- **Esperienza personalizzabile:** temi Classico, Notte, Pastello, Neon e Blu; definizioni orizzontali e verticali e possibilità di mostrare o nascondere le soluzioni.
- **Esportazione:** genera file **PNG, PDF e SVG**. Per i PNG puoi scegliere la risoluzione, usare celle trasparenti e creare anche un'immagine con la soluzione.
- **Condivisione:** salva le immagini nella Galleria o invia i risultati alle altre app del dispositivo.

## 🧠 Il generatore

Il motore normalizza le parole, analizza le lettere compatibili e prova diverse disposizioni. Combina tecniche di **CSP**, **MRV**, euristiche di grado, **beam search** e backtracking.

Le configurazioni sono valutate considerando incroci, densità, compattezza, simmetria, spazi vuoti, parole isolate e proporzioni della griglia. Se non è possibile incrociare tutto, il generatore cerca comunque una disposizione valida e ricca di incroci.

## 📲 Installare l'APK Android

1. Apri la pagina [Build Android APK su GitHub Actions](https://github.com/samuelemuccio/Cruciverba/actions/workflows/build-apk.yml).
2. Entra nell'ultima esecuzione **completata con successo**.
3. Nella sezione **Artifacts**, scarica `crossword-debug-apk`.
4. Estrai lo ZIP e installa l'APK su Android. Potrebbe essere necessario autorizzare l'installazione da quella sorgente.

> Gli artifact di GitHub Actions hanno una scadenza e non sono download permanenti. Questa è una build di debug; per distribuire l'app in modo stabile è preferibile pubblicare una release dedicata.

## 🛠️ Stack tecnologico

- **Kotlin**, Android SDK e **Jetpack Compose** con Material 3
- **Room** per i dati locali; Coroutines e Flow per la gestione asincrona
- **Navigation Compose** per la navigazione
- **Retrofit**, OkHttp e Moshi per le integrazioni di rete
- **Firebase AI** per la formattazione assistita
- **Robolectric** e **Roborazzi** per i test
- **Gradle Kotlin DSL** per la build

## 🚀 Avvio locale

### Requisiti

- Android Studio
- JDK 17
- Android SDK con API 36
- Dispositivo o emulatore Android 7.0 (API 24) o successivo

### Clonazione, test e build

```bash
git clone https://github.com/samuelemuccio/Cruciverba.git
cd Cruciverba

# Test unitari
./gradlew testDebugUnitTest

# APK di debug
./gradlew assembleDebug
```

Su **Windows**, usa il wrapper batch:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

L'APK viene generato in `app/build/outputs/apk/debug/`.

## 📁 Struttura del progetto

```text
app/src/main/java/com/samuele/crossword/
├── data/       # Database Room, DAO e repository
├── domain/     # Parsing e analisi delle parole
├── engine/     # Generazione, solver e ottimizzazione
├── export/     # Esportazione PNG, PDF e SVG
└── ui/         # Schermate Compose, componenti e temi

.github/workflows/build-apk.yml  # Build automatica dell'APK
metadata.json                    # Metadati e capacità dell'app
gradle/libs.versions.toml        # Versioni centralizzate delle dipendenze
```

## 🐛 Problemi e suggerimenti

Hai trovato un bug o vuoi proporre una funzionalità? Apri una [segnalazione su GitHub Issues](https://github.com/samuelemuccio/Cruciverba/issues).
