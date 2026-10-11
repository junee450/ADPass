from PIL import Image
from pathlib import Path
p=Path(__file__).parent
out=p/'fixtures'; out.mkdir(exist_ok=True)
for n in range(18,33):
 im=Image.open(p/f'frame-{n:03d}.png').transpose(Image.Transpose.ROTATE_90)
 kind='waiting' if n<25 else 'ad'
 im.save(out/f'{kind}-phone-{n:03d}.png')
