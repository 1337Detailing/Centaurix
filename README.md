# Centaurix 0.4.0

Application indépendante Android pour vérifier la communication Bluetooth avec la Lost Vape Centaurus BT200. Non affiliée au fabricant.

## Diagnostic
- Scanner BLE sans filtre, haute sensibilité, 20 secondes ; affiche chaque appareil détecté et son RSSI.
- Permissions Appareils à proximité et Localisation précise, rappel d'activer Localisation dans Android.
- Appareils Bluetooth déjà associés.
- Connexion GATT, service FFA0, activation des notifications, requête officielle d'état `AA550100007C14` et contrôle CRC.
- Partage manuel du journal ; aucune transmission automatique.

**Avant le scan** : BT200 > Menu > Bluetooth > ON > **presser le bouton central pour valider**. Activer Bluetooth + Localisation sur téléphone ; fermer l'application officielle Centaurus.

Ne modifie ni puissance, ni chauffe, ni profils, ni firmware. Projet de test : connexion matérielle non encore validée.
