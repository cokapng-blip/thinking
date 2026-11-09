# Ícone do Mod

Coloque um arquivo `icon.png` (64x64 pixels) em:
```
src/main/resources/assets/tlgdassault/icon.png
```

## Sugestão de Design

Um ícone simples representando:
- ⚔️ Duas espadas cruzadas (combate)
- 🌍 Globo/grade territorial (TLGD)
- 🏴 Bandeira (captura de região)
- 🚢 Silhueta de navio (combate naval)

## Cores sugeridas (paleta CCNet style)
- Vermelho escuro: `#8B0000` (combate)
- Dourado: `#FFD700` (recompensas/conquista)
- Cinza escuro: `#2F2F2F` (fundo)
- Branco: `#FFFFFF` (detalhes)

## Ferramentas para criar
- **Online**: https://www.piskelapp.com/ (pixel art)
- **Desktop**: Aseprite, Paint.NET, GIMP
- **SVG → PNG**: Inkscape, https://convertio.co/svg-png/

## Exemplo rápido (SVG)
```svg
<svg width="64" height="64" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
  <rect width="64" height="64" fill="#2F2F2F" rx="8"/>
  <path d="M16 20 L48 44 M48 20 L16 44" stroke="#8B0000" stroke-width="3" stroke-linecap="round"/>
  <circle cx="32" cy="32" r="14" fill="none" stroke="#FFD700" stroke-width="2"/>
  <path d="M32 18 L32 14 M32 50 L32 46 M18 32 L14 32 M50 32 L46 32" stroke="#FFD700" stroke-width="2" stroke-linecap="round"/>
</svg>
```

Converta para PNG 64x64 e salve como `icon.png`.