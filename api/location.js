export default function handler(req,res){
  res.setHeader('Cache-Control','private, no-store');
  res.setHeader('Content-Type','application/json; charset=utf-8');
  res.setHeader('X-Content-Type-Options','nosniff');
  const h=req.headers;
  const latitude=Number(h['x-vercel-ip-latitude']);
  const longitude=Number(h['x-vercel-ip-longitude']);
  const valid=(h['x-vercel-ip-latitude']!==undefined&&h['x-vercel-ip-longitude']!==undefined&&Number.isFinite(latitude)&&Number.isFinite(longitude)&&latitude>=-90&&latitude<=90&&longitude>=-180&&longitude<=180);
  if(!valid)return res.status(200).json({found:false});
  const city=String(h['x-vercel-ip-city']||'').slice(0,100);
  let cityName=city;try{cityName=decodeURIComponent(city)}catch{}
  const region=String(h['x-vercel-ip-country-region']||'').slice(0,15);
  const country=String(h['x-vercel-ip-country']||'').slice(0,3);
  return res.status(200).json({found:true,latitude,longitude,city:cityName,region,country,accuracy:'approximate'});
}
