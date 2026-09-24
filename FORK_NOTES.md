# PocketS-Tune — fork do Pulse pro AYANEO Pocket S 2K (SG8275 / kalama)

Fork de [keiretrogaming/pulse](https://github.com/keiretrogaming/pulse) (GPL-2.0),
adaptado para dispositivos que não expõem o binder customizado `PServerBinder`
(AYN/Retroid), mas que têm root via Magisk disponível por outros meios.

## O que mudou

1. **`app/src/main/java/com/kei/pulse/root/RootExec.kt`**
   Reescrito para rodar comandos via `su` (Magisk) usando `ProcessBuilder`,
   em vez de `ServiceManager.getService("PServerBinder")` + `binder.transact()`.
   A interface pública (`pServerAvailable`, `executeAsRoot(cmd): Result<String?>`)
   foi mantida idêntica de propósito, então `RootCommandRunner`, `RootSupport` e
   `PServerSysfsReader` não precisaram de nenhuma alteração.

2. **`app/src/main/assets/bundled_profiles/SG8275.json`** (novo)
   Perfil bundled para o AYANEO Pocket S 2K, com os 3 clusters de CPU
   (Efficiency: CPU0-2, Performance: CPU3-6, Prime: CPU7) e a tabela de
   frequências de GPU (Adreno, kgsl-3d0), extraídos diretamente do
   dispositivo via `adb shell su -c cat ...`.

## Requisitos

- AYANEO Pocket S 2K com Magisk instalado (via patch de `init_boot` +
  `fastboot flash init_boot`, sem necessidade de bootloader unlock via EDL).
- Conceder acesso root ao app quando o Magisk perguntar no primeiro uso.

## Build

Mesmo processo do projeto original:

```
./gradlew testDebugUnitTest assembleDebug
```

APK de debug sai em `app/build/outputs/apk/debug/`.

## Créditos

- Todo o app (UI, AutoTDP, HUD, temas, fan curve) é trabalho do
  [keiretrogaming/pulse](https://github.com/keiretrogaming/pulse), que por sua vez
  usa a técnica no-root originalmente do
  [AurelioB/ClusterTune](https://github.com/AurelioB/ClusterTune).
- Método de root sem bootloader unlock (Root Script + Magisk patch de
  `init_boot`) documentado no guia "How to setup your Ayaneo Pocket EVO nicely"
  (comunidade Discord do EVO), aplicável ao Pocket S 2K por compartilhar o
  mesmo SoC G3x Gen 2 / plataforma kalama.
