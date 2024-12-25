@echo off
echo Updating pip...
py -3.10 -m pip install --upgrade pip

echo.
echo Installing PyTorch (CPU version)...
py -3.10 -m pip install torch torchvision torchaudio

echo.
echo Installing other dependencies...
py -3.10 -m pip install -r requirements.txt

echo.
echo Dependencies installed successfully!
echo The first time you run the server, it will download the YOLOv8 model (about 20MB)
pause
