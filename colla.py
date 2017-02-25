#!/usr/bin/env python

from PIL import Image

import multiprocessing
try:
    cpus = multiprocessing.cpu_count()
except NotImplementedError:
    print("Failed to detect number of CPUs, assuming 2")
    cpus = 2   # arbitrary default


import os, sys
import time

def main():
    for num_images in [4, 8, 16, 32]:
        for processes in [8, 4, 2, 1]:
            start = time.time()
            grid(num_images=num_images, processes=processes)
            print("{}s for {} images on {} processes".format(time.time() - start, num_images, processes))


def grid(num_images, processes):

    base = "/Users/katz/Desktop/Tria/2014"

    pool = multiprocessing.Pool(processes=processes)

    image_paths = [os.path.join(base, x) for x in os.listdir(base)[:num_images]]

    images = pool.map(Image.open, image_paths)
    squares = pool.map(crop_square, images)

    dimensions = [(x.width, x.height) for x in squares]
    # print('\n'.join(['{} x {}'.format(x[0], x[1]) for x in dimensions]))





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

