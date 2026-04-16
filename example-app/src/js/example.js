import { ShelfCamera } from 'capacitor-shelf-camera';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    ShelfCamera.echo({ value: inputValue })
}
