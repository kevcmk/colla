#!/usr/bin/env python

from PIL import Image

import os, sys
import json

def main():
    base = "/Users/katz/Desktop/Tria/2014"

    image_paths = [os.path.join(base, x) for x in os.listdir(base)[:12]]
    images = [Image.open(x) for x in image_paths]
    squares = [crop_square(x) for x in images]
    dimensions = [(x.width, x.height) for x in squares]
    print('\n'.join(['{} x {}'.format(x[0], x[1]) for x in dimensions]))





# Return the largest square crop
def crop_square(image: Image) -> Image:
    if image.height == image.width:
        return image
    elif image.height < image.width:
        # Wide Image
        edge_length = image.height
        left_x = (image.width - edge_length) / 2
        right_x = left_x + edge_length
        return image.crop((left_x, 0, right_x, edge_length))
    else:
        # Tall Image
        edge_length = image.width
        top_y = (image.height - edge_length) / 2
        bottom_y = top_y + edge_length
        return image.crop((0, top_y, edge_length, bottom_y))

        

if __name__ == '__main__':

    for i in [Image.new('RGB', (300, 400)),
              Image.new('RGB', (400, 300)),
              Image.new('RGB', (300, 300))]:
        assert crop_square(i).width == 300
        assert crop_square(i).height == 300

    main()

