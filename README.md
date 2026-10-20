# Centaurix v0.5.0

App Android de diagnostic BLE pour la Lost Vape Centaurus BT200. Non affiliée au constructeur.

## Communication prouvée

Un test sur une vraie BT200 a confirmé la détection Bluetooth, la connexion GATT, le service FFA0, les notifications et la réponse d'état à `AA550100007C14`.

La v0.4 rejetait cette réponse car elle attendait le CRC16/MODBUS dans l'ordre des octets de la requête (LE). La réponse réelle utilise **l'ordre inverse BE** : CRC calculé `0x9480`, octets reçus `94 80`. La v0.5 accepte les deux ordres, sans désactiver la vérification.

Affichage en lecture seule : pourcentages des deux accus, profil actif, mode, watts (Power/Turbo), compteur bouffées et temps rapporté par le firmware. Journaux de réponses multiparties et bouton de partage.

## Test

BT200 : Menu > Bluetooth > ON et confirmer avec le bouton central. Fermer l'application officielle.
Android : activer Bluetooth + Localisation, autoriser les permissions, scanner et sélectionner BT200.

Aucune commande de réglage, chauffe ou firmware envoyée : seule la requête de lecture d'état est utilisée.

## Vérifications

`javac -d /tmp/centaurix-protocol-test app/src/main/java/fr/centaurix/app/Bt200FrameCodec.java tests/ProtocolTest.java && java -cp /tmp/centaurix-protocol-test fr.centaurix.app.ProtocolTest`

Le workflow GitHub Actions compile l'APK et lance les tests sur le vecteur de réponse matériel.
