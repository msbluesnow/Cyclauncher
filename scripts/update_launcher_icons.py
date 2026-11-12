import os
import re
import shutil
import subprocess
import numpy as np
from PIL import Image

INKSCAPE_PATH = r"C:\Program Files\Inkscape\bin\inkscape.com"

def main():
    print("--- 1. Cleaning SVG files (removing XML comments) ---")
    with open('assets/cyclauncher_inkscape.svg', 'r', encoding='utf-8') as f:
        svg_content = f.read()

    # Remove all XML comments
    clean_svg = re.sub(r'[ \t]*<!--.*?-->\n?', '', svg_content)
    
    # Verify no comments remain
    remaining = re.findall(r'<!--.*?-->', clean_svg, re.DOTALL)
    if remaining:
        raise ValueError(f"Comments still found: {remaining}")

    with open('assets/cyclauncher_inkscape.svg', 'w', encoding='utf-8') as f:
        f.write(clean_svg)
    print("Cleaned assets/cyclauncher_inkscape.svg successfully.")

    # Update assets/Cyclauncher.svg to master new logo
    clean_cyclauncher_svg = clean_svg.replace(
        'sodipodi:docname="cyclauncher_inkscape.svg"',
        'sodipodi:docname="Cyclauncher.svg"'
    ).replace(
        'inkscape:export-filename="cyclauncher_inkscape.png"',
        'inkscape:export-filename="logo.png"'
    )
    with open('assets/Cyclauncher.svg', 'w', encoding='utf-8') as f:
        f.write(clean_cyclauncher_svg)
    print("Updated assets/Cyclauncher.svg successfully.")

    print("\n--- 2. Exporting master logo.png (1024x1024) via Inkscape ---")
    subprocess.run([
        INKSCAPE_PATH,
        'assets/cyclauncher_inkscape.svg',
        '--export-filename=assets/logo.png',
        '--export-width=1024',
        '--export-height=1024'
    ], check=True)
    print("Exported assets/logo.png.")

    # Copy to Fastlane metadata
    fastlane_paths = [
        'fastlane/metadata/android/en-US/images/icon.png',
        'app/src/main/fastlane/metadata/android/en-US/images/icon.png'
    ]
    for p in fastlane_paths:
        os.makedirs(os.path.dirname(p), exist_ok=True)
        shutil.copyfile('assets/logo.png', p)
        print(f"Updated {p}")

    print("\n--- 3. Exporting ultra-high-res emblem for sub-asset generation ---")
    temp_emblem_path = 'assets/temp_emblem_4096.png'
    subprocess.run([
        INKSCAPE_PATH,
        'assets/cyclauncher_inkscape.svg',
        '--export-filename=' + temp_emblem_path,
        '--export-width=4096',
        '--export-height=4096'
    ], check=True)

    im_4096 = Image.open(temp_emblem_path)
    bbox = im_4096.getbbox()
    emblem_master = im_4096.crop(bbox)
    print(f"Emblem master cropped size: {emblem_master.size}")

    print("\n--- 4. Generating app/src/main/ic_launcher-playstore.png (512x512) ---")
    playstore_canvas = Image.new('RGBA', (512, 512), (255, 255, 255, 255))
    playstore_w = 320
    playstore_h = int(round(playstore_w * emblem_master.height / emblem_master.width))
    resized_playstore = emblem_master.resize((playstore_w, playstore_h), Image.Resampling.LANCZOS)
    pos_x = (512 - playstore_w) // 2
    pos_y = (512 - playstore_h) // 2
    playstore_canvas.paste(resized_playstore, (pos_x, pos_y), resized_playstore)
    playstore_canvas.save('app/src/main/ic_launcher-playstore.png', 'PNG')
    print("Saved app/src/main/ic_launcher-playstore.png.")

    print("\n--- 5. Generating Adaptive Icon Foregrounds (ic_launcher_foreground.webp) ---")
    densities_fg = {
        'mdpi': (108, 55),
        'hdpi': (162, 82),
        'xhdpi': (216, 110),
        'xxhdpi': (324, 165),
        'xxxhdpi': (432, 220),
    }
    for density, (canvas_dim, target_w) in densities_fg.items():
        fg_canvas = Image.new('RGBA', (canvas_dim, canvas_dim), (0, 0, 0, 0))
        target_h = int(round(target_w * emblem_master.height / emblem_master.width))
        resized_emblem = emblem_master.resize((target_w, target_h), Image.Resampling.LANCZOS)
        px = (canvas_dim - target_w) // 2
        py = (canvas_dim - target_h) // 2
        fg_canvas.paste(resized_emblem, (px, py), resized_emblem)
        out_path = f'app/src/main/res/mipmap-{density}/ic_launcher_foreground.webp'
        fg_canvas.save(out_path, 'WEBP', lossless=True)
        print(f"Saved {out_path} ({canvas_dim}x{canvas_dim}, logo {target_w}x{target_h})")

    print("\n--- 6. Generating Legacy Square and Round Icons ---")
    densities_legacy = {
        'mdpi': (48, 30),
        'hdpi': (72, 45),
        'xhdpi': (96, 60),
        'xxhdpi': (144, 90),
        'xxxhdpi': (192, 120),
    }

    for density, (canvas_dim, target_w) in densities_legacy.items():
        target_h = int(round(target_w * emblem_master.height / emblem_master.width))
        resized_emblem = emblem_master.resize((target_w, target_h), Image.Resampling.LANCZOS)

        # 6a. Square (ic_launcher.webp)
        sq_path = f'app/src/main/res/mipmap-{density}/ic_launcher.webp'
        sq_orig = Image.open(sq_path)
        sq_arr = np.array(sq_orig)
        # Clear interior (where alpha == 255) to pure white
        sq_arr[sq_arr[:,:,3] == 255, :3] = 255
        sq_base = Image.fromarray(sq_arr)
        px = (canvas_dim - target_w) // 2
        py = (canvas_dim - target_h) // 2
        sq_base.paste(resized_emblem, (px, py), resized_emblem)
        sq_base.save(sq_path, 'WEBP', lossless=True)
        print(f"Saved {sq_path} ({canvas_dim}x{canvas_dim})")

        # 6b. Round (ic_launcher_round.webp)
        rd_path = f'app/src/main/res/mipmap-{density}/ic_launcher_round.webp'
        rd_orig = Image.open(rd_path)
        rd_arr = np.array(rd_orig)
        # Clear interior (where alpha == 255) to pure white
        rd_arr[rd_arr[:,:,3] == 255, :3] = 255
        rd_base = Image.fromarray(rd_arr)
        rd_base.paste(resized_emblem, (px, py), resized_emblem)
        rd_base.save(rd_path, 'WEBP', lossless=True)
        print(f"Saved {rd_path} ({canvas_dim}x{canvas_dim})")

    # Clean up temp file
    im_4096.close()
    if os.path.exists(temp_emblem_path):
        os.remove(temp_emblem_path)
    print("\nTemporary emblem file cleaned up. Icon update complete!")

if __name__ == '__main__':
    main()
